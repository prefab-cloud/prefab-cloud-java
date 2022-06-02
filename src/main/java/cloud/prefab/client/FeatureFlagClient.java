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

  private static final int DISTRIBUTION_SPACE = 1000;
  private static final HashFunction hash = Hashing.murmur3_32();
  private static final long UNSIGNED_INT_MAX = Integer.MAX_VALUE + (long) Integer.MAX_VALUE;

  private final PrefabCloudClient baseClient;

  private RandomProviderIF randomProvider = new RandomProvider();

  public FeatureFlagClient(PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
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
    final ConfigClient configClient = baseClient.configClient();
    final Optional<Prefab.ConfigValue> configValue = configClient.get(feature);
    final Optional<Prefab.Config> configObj = configClient.getConfigObj(feature);

    if (configValue.isPresent() && configValue.get().getTypeCase() == Prefab.ConfigValue.TypeCase.FEATURE_FLAG) {
      return Optional.of(getVariant(feature, lookupKey, attributes, configValue.get().getFeatureFlag(), configObj.get().getVariantsList()));
    } else {
      return Optional.empty();
    }

  }

  protected boolean isOnFor(Prefab.FeatureFlag featureFlag, String feature, Optional<String> lookupKey, Map<String, String> attributes, List<Prefab.FeatureFlagVariant> variants) {
    return getVariant(feature, lookupKey, attributes, featureFlag, variants).getBool();
  }

//  Optional<Prefab.FeatureFlagVariant> evaluate(String feature, Optional<String> lookupKey, Map<String, String> attributes, List<Prefab.FeatureFlagVariant> variants) {
//
//  }

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


  Prefab.FeatureFlagVariant getVariant(String featureName, Optional<String> lookupKey, Map<String, String> attributes,
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
      if (criteriaMatch(rule, lookupKey, attributes)) {
        variantWeights = rule.getVariantWeightsList();
      }
    }

    double pctThroughDistribution = randomProvider.random();
    if (lookupKey.isPresent()) {
      pctThroughDistribution = getUserPct(lookupKey.get(), baseClient.getProjectId(), featureName);
    }

    int variantIdx = getVariantIdxFromWeights(variantWeights, pctThroughDistribution, featureName);
    return getVariantObj(variants, variantIdx);
  }


  Prefab.FeatureFlagVariant getVariantObj(List<Prefab.FeatureFlagVariant> variants, int variantIdx) {
    return variants.get(variantIdx);
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

  private boolean criteriaMatch(Prefab.Rule rule, Optional<String> lookupKey, Map<String, String> attributes) {
    switch (rule.getCriteria().getOperator()) {
      case ALWAYS_TRUE:
        return true;
      case LOOKUP_KEY_IN:
        if (!lookupKey.isPresent()) {
          return false;
        }
        return rule.getCriteria().getValuesList().contains(lookupKey.get());
      case LOOKUP_KEY_NOT_IN:
        if (!lookupKey.isPresent()) {
          return false;
        }
        return !rule.getCriteria().getValuesList().contains(lookupKey.get());
      case IN_SEG:
        return segmentMatches(rule.getCriteria().getValuesList(), lookupKey, attributes).stream().anyMatch(v -> v == true);
      case NOT_IN_SEG:
        return segmentMatches(rule.getCriteria().getValuesList(), lookupKey, attributes).stream().noneMatch(v -> v == true);
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
      final Optional<Prefab.ConfigValue> segment = baseClient.configClient().get(segmentKey);
      if (segment.isPresent() && segment.get().getTypeCase() == Prefab.ConfigValue.TypeCase.SEGMENT) {
        return segmentMatch(segment.get().getSegment(), lookupKey, attributes);
      } else {
        // missing segment
        return false;
      }
    }).collect(Collectors.toList());
  }

  private boolean segmentMatch(Prefab.Segment segment, Optional<String> lookupKey, Map<String, String> attributes) {
    if (!lookupKey.isPresent()) {
      return false;
    }
//    boolean includes = segment.getIncludesList().contains(lookupKey.get());
//    boolean excludes = segment.getExcludesList().contains(lookupKey.get());
//    return includes && !excludes;
    return false;
  }

}
