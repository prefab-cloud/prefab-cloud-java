package cloud.prefab.client;

import cloud.prefab.client.util.RandomProvider;
import cloud.prefab.client.util.RandomProviderIF;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.protobuf.ProtocolStringList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  public boolean featureIsOn(String feature) {
    return featureIsOnFor(feature, Optional.empty(), Maps.newHashMap());
  }

  public boolean featureIsOnFor(String feature, Optional<String> key, Map<String, String> attributes) {

    final ConfigClient configClient = baseClient.configClient();
    final Optional<Prefab.ConfigValue> configValue = configClient.get(feature);

    if (configValue.isPresent() && configValue.get().getTypeCase() == Prefab.ConfigValue.TypeCase.FEATURE_FLAG) {
      return isOnFor(configValue.get().getFeatureFlag(), feature, key, attributes);
    } else {
      return false;
    }
  }

  public void upsert(String key, Prefab.FeatureFlag featureFlag) {
    baseClient.configClient().upsert(key, Prefab.ConfigValue.newBuilder()
        .setFeatureFlag(featureFlag)
        .build());
  }

  protected boolean isOnFor(Prefab.FeatureFlag featureFlag, String feature, Optional<String> lookupKey, Map<String, String> attributes) {
    return getVariant(feature, lookupKey, attributes, featureFlag).getBool();
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

  public FeatureFlagClient setRandomProvider(RandomProviderIF randomProvider) {
    this.randomProvider = randomProvider;
    return this;
  }


  Prefab.FeatureFlagVariant getVariant(String featureName, Optional<String> lookupKey, Map<String, String> attributes, Prefab.FeatureFlag featureObj) {

    if (!featureObj.getActive()) {
      return getVariantObj(featureObj, featureObj.getInactiveVariantIdx());
    }

    // default
    Prefab.VariantDistribution variantDistribution = featureObj.getDefault();


    // if user_targets.match
    for (Prefab.UserTarget userTarget : featureObj.getUserTargetsList()) {
      if (lookupKey.isPresent() && userTarget.getIdentifiersList().contains(lookupKey.get())) {
        return getVariantObj(featureObj, userTarget.getVariantIdx());
      }
    }

    // if rules.match
    for (Prefab.Rule rule : featureObj.getRulesList()) {
      if (criteriaMatch(rule, lookupKey, attributes)) {
        variantDistribution = rule.getDistribution();
      }
    }

    if (variantDistribution.getTypeCase() == Prefab.VariantDistribution.TypeCase.VARIANT_IDX) {
      return getVariantObj(featureObj, variantDistribution.getVariantIdx());
    } else { //Prefab.VariantDistribution.TypeCase.VARIANT_WEIGHTS
      double pctThroughDistribution = randomProvider.random();
      if (lookupKey.isPresent()) {
        pctThroughDistribution = getUserPct(lookupKey.get(), baseClient.getProjectId(), featureName);
      }
      int distribution_bucket = (int) (DISTRIBUTION_SPACE * pctThroughDistribution);

      int variantIdx = getVariantIdxFromWeights(variantDistribution.getVariantWeights(), distribution_bucket, featureName);
      return getVariantObj(featureObj, variantIdx);
    }

  }


  Prefab.FeatureFlagVariant getVariantObj(Prefab.FeatureFlag featureObj, int variantIdx) {
    return featureObj.getVariants(variantIdx);
  }


  int getVariantIdxFromWeights(Prefab.VariantWeights variantWeights, int distributionBucket, String featureName) {
    int sum = 0;
    for (Prefab.VariantWeight variantWeight : variantWeights.getWeightsList()) {
      if (distributionBucket < sum + variantWeight.getWeight()) {
        return variantWeight.getVariantIdx();
      } else {
        sum += variantWeight.getWeight();
      }
    }
    // variants didn't add up to 100%
    return variantWeights.getWeightsList().get(variantWeights.getWeightsCount() - 1).getVariantIdx();
  }

  private boolean criteriaMatch(Prefab.Rule rule, Optional<String> lookupKey, Map<String, String> attributes) {
    switch (rule.getCriteria().getOperator()) {
      case IN:
        if (!lookupKey.isPresent()) {
          return false;
        }
        return rule.getCriteria().getValuesList().contains(lookupKey.get());
      case NOT_IN:
        if (!lookupKey.isPresent()) {
          return false;
        }
        return !rule.getCriteria().getValuesList().contains(lookupKey.get());
      case IN_SEG:
        return segmentMatches(rule.getCriteria().getValuesList(), lookupKey, attributes).stream().allMatch(v -> v == true);
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
    boolean includes = segment.getIncludesList().contains(lookupKey.get());
    boolean excludes = segment.getExcludesList().contains(lookupKey.get());
    return includes && !excludes;
  }

}
