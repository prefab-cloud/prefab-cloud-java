package cloud.prefab.client;

import cloud.prefab.client.util.RandomProvider;
import cloud.prefab.client.util.RandomProviderIF;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.protobuf.ProtocolStringList;

import java.util.*;
import java.util.stream.Collectors;

public class FeatureFlagClient {

  private static final HashFunction hash = Hashing.murmur3_32();
  private static final long UNSIGNED_INT_MAX = Integer.MAX_VALUE + (long) Integer.MAX_VALUE;

  private final ConfigStore configStore;

  private RandomProviderIF randomProvider = new RandomProvider();

  public FeatureFlagClient(ConfigStore configStore) {
    this.configStore = configStore;
  }

  /**
   * Simplified method for boolean flags. Returns false if flag is not defined.
   *
   * @param feature
   * @return
   */
  public boolean featureIsOn(String feature) {
    return featureIsOnFor(feature, Optional.empty(), Maps.newHashMap());
  }

  /**
   * Simplified method for boolean flags. Returns false if flag is not defined.
   *
   * @param feature
   * @param lookupKey
   * @param attributes
   * @return
   */
  public boolean featureIsOnFor(String feature, Optional<String> lookupKey, Map<String, String> attributes) {
    return isOn(get(feature, lookupKey, attributes));
  }


  /**
   * Fetch a feature flag and evaluate
   *
   * @param feature
   * @param lookupKey
   * @param attributes
   * @return
   */
  public Optional<Prefab.FeatureFlagVariant> get(String feature, Optional<String> lookupKey, Map<String, String> attributes) {
    final Optional<Prefab.ConfigValue> configValue = configStore.get(feature);
    final Optional<Prefab.Config> configObj = configStore.getConfigObj(feature);

    if (configValue.isPresent() && configValue.get().getTypeCase() == Prefab.ConfigValue.TypeCase.FEATURE_FLAG) {
      return getVariant(feature, lookupKey, attributes, configValue.get().getFeatureFlag(), configObj.get().getVariantsList());
    } else {
      return Optional.empty();
    }

  }

  protected boolean isOnFor(Prefab.FeatureFlag featureFlag, String feature, Optional<String> lookupKey, Map<String, String> attributes, List<Prefab.FeatureFlagVariant> variants) {
    return isOn(getVariant(feature, lookupKey, attributes, featureFlag, variants));
  }

  double getUserPct(String lookupKey, long projectId, String featureName) {
    final String toHash = String.format("%d%s%s", projectId, featureName, lookupKey);
    final HashCode hashCode = hash.hashBytes(toHash.getBytes());
    return pct(hashCode.asInt());
  }

  private double pct(int signedInt) {
    long y = signedInt & 0x00000000ffffffffL;
    return y / (double) (UNSIGNED_INT_MAX);
  }

  private boolean isOn(Optional<Prefab.FeatureFlagVariant> featureFlagVariant) {
    if (featureFlagVariant.isPresent()) {
      if (featureFlagVariant.get().hasBool()) {
        return featureFlagVariant.get().getBool();
      } else {
        // TODO log
        return false;
      }
    } else {
      return false;
    }
  }

  public FeatureFlagClient setRandomProvider(RandomProviderIF randomProvider) {
    this.randomProvider = randomProvider;
    return this;
  }


  Optional<Prefab.FeatureFlagVariant> getVariant(String featureName, Optional<String> lookupKey, Map<String, String> attributes,
                                       Prefab.FeatureFlag featureObj, List<Prefab.FeatureFlagVariant> variants) {

    if (!featureObj.getActive()) {
      return getVariantObj(variants, featureObj.getInactiveVariantIdx());
    }

    //default to inactive
    List<Prefab.VariantWeight> variantWeights = new ArrayList<>();
    variantWeights.add(Prefab.VariantWeight.newBuilder()
        .setVariantIdx(featureObj.getInactiveVariantIdx())
        .setWeight(1)
        .build());


    // if rules.match
    for (Prefab.Rule rule : featureObj.getRulesList()) {
      if (criteriaMatch(rule.getCriteria(), lookupKey, attributes)) {
        variantWeights = rule.getVariantWeightsList();
        break;
      }
    }

    double pctThroughDistribution = randomProvider.random();
    if (lookupKey.isPresent()) {
      pctThroughDistribution = getUserPct(lookupKey.get(), configStore.getProjectId(), featureName);
    }

    int variantIdx = getVariantIdxFromWeights(variantWeights, pctThroughDistribution, featureName);
    return getVariantObj(variants, variantIdx);
  }


  Optional<Prefab.FeatureFlagVariant> getVariantObj(List<Prefab.FeatureFlagVariant> variants, int variantIdx) {
    if(variantIdx > 0 && variantIdx <= variants.size()){
      return Optional.of(variants.get(variantIdx - 1));//1 based
    }else{
      return Optional.empty();
    }
  }


  int getVariantIdxFromWeights(List<Prefab.VariantWeight> variantWeights, double pctThroughDistribution, String featureName) {
    Optional<Integer> distributionSpace = variantWeights.stream().map(Prefab.VariantWeight::getWeight).reduce(Integer::sum);
    int bucket = (int) (distributionSpace.get() * pctThroughDistribution);
    int sum = 0;
    for (Prefab.VariantWeight variantWeight : variantWeights) {
      if (bucket < sum + variantWeight.getWeight()) {
        return variantWeight.getVariantIdx();
      } else {
        sum += variantWeight.getWeight();
      }
    }
    // variants didn't add up to 100%
    return variantWeights.get(0).getVariantIdx();
  }

  private boolean criteriaMatch(Prefab.Criteria criteria, Optional<String> lookupKey, Map<String, String> attributes) {
    switch (criteria.getOperator()) {
      case ALWAYS_TRUE:
        return true;
      case LOOKUP_KEY_IN:
        if (!lookupKey.isPresent()) {
          return false;
        }
        return criteria.getValuesList().contains(lookupKey.get());
      case LOOKUP_KEY_NOT_IN:
        if (!lookupKey.isPresent()) {
          return false;
        }
        return !criteria.getValuesList().contains(lookupKey.get());
      case IN_SEG:
        return segmentMatches(criteria.getValuesList(), lookupKey, attributes).stream().anyMatch(v -> v);
      case NOT_IN_SEG:
        return segmentMatches(criteria.getValuesList(), lookupKey, attributes).stream().noneMatch(v -> v);
      case PROP_IS_ONE_OF:
        return criteria.getValuesList().contains(attributes.get(criteria.getProperty()));
      case PROP_IS_NOT_ONE_OF:
        return !criteria.getValuesList().contains(attributes.get(criteria.getProperty()));
    }
    // Unknown Operator
    return false;
  }

  /*
    evaluate each segment key and return whether each one matches
    there should be an associated segment available as a standard config obj
   */
  private List<Boolean> segmentMatches(ProtocolStringList segmentKeys, Optional<String> lookupKey, Map<String, String> attributes) {
    return segmentKeys.stream().map(segmentKey -> {
      final Optional<Prefab.ConfigValue> segment = configStore.get(segmentKey);
      if (segment.isPresent() && segment.get().getTypeCase() == Prefab.ConfigValue.TypeCase.SEGMENT) {
        return segmentMatch(segment.get().getSegment(), lookupKey, attributes);
      } else {
        // missing segment
        return false;
      }
    }).collect(Collectors.toList());
  }

  private boolean segmentMatch(Prefab.Segment segment, Optional<String> lookupKey, Map<String, String> attributes) {
    final boolean anyMatch = segment.getCriterionList().stream().map(c -> criteriaMatch(c, lookupKey, attributes)).anyMatch(b -> b);
    return anyMatch;
  }

}
