package cloud.prefab.client;

import cloud.prefab.client.util.RandomProvider;
import cloud.prefab.client.util.RandomProviderIF;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.util.Map;
import java.util.Optional;

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

    final Prefab.VariantDistribution variantDistribution = featureObj.getDefault();

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

//   def get_variant(feature_name, lookup_key, attributes, feature_obj)
//      if !feature_obj.active
//        return get_variant_obj(feature_obj, feature_obj.inactive_variant_idx)
//      end
//
//      variant_distribution = feature_obj.default
//
//      # if user_targets.match
//      feature_obj.user_targets.each do |target|
//        if (target.identifiers.include? lookup_key)
//          return get_variant_obj(feature_obj, target.variant_idx)
//        end
//      end
//
//      # if rules.match
//      feature_obj.rules.each do |rule|
//        if criteria_match?(rule, lookup_key, attributes)
//          variant_distribution = rule.distribution
//        end
//      end
//
//      if variant_distribution.type == :variant_idx
//        variant_idx = variant_distribution.variant_idx
//      else
//        percent_through_distribution = rand()
//        if lookup_key
//          percent_through_distribution = get_user_pct(feature_name, lookup_key)
//        end
//        distribution_bucket = DISTRIBUTION_SPACE * percent_through_distribution
//
//        variant_idx = get_variant_idx_from_weights(variant_distribution.variant_weights.weights, distribution_bucket, feature_name)
//      end
//
//      return get_variant_obj(feature_obj, variant_idx)
//    end


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

//
//    def get_user_pct(feature, lookup_key)
//      to_hash = "#{@base_client.project_id}#{feature}#{lookup_key}"
//      int_value = Murmur3.murmur3_32(to_hash)
//      int_value / MAX_32_FLOAT
//    end
//
//    def criteria_match?(rule, lookup_key, attributes)
//      if rule.criteria.operator == :IN
//        return rule.criteria.values.include?(lookup_key)
//      elsif rule.criteria.operator == :NOT_IN
//        return !rule.criteria.values.include?(lookup_key)
//      elsif rule.criteria.operator == :IN_SEG
//        return segment_matches(rule.criteria.values, lookup_key, attributes).any?
//      elsif rule.criteria.operator == :NOT_IN_SEG
//        return segment_matches(rule.criteria.values, lookup_key, attributes).none?
//      end
//      @base_client.log.info("Unknown Operator")
//      false
//    end
//
//    # evaluate each segment key and return whether each one matches
//    # there should be an associated segment available as a standard config obj
//    def segment_matches(segment_keys, lookup_key, attributes)
//      segment_keys.map do |segment_key|
//        segment = @base_client.get(segment_key)
//        if segment.nil?
//          @base_client.log.info("Missing Segment")
//          false
//        else
//          segment_match?(segment, lookup_key, attributes)
//        end
//      end
//    end
//
//    def segment_match?(segment, lookup_key, attributes)
//      includes = segment.includes.include?(lookup_key)
//      excludes = segment.excludes.include?(lookup_key)
//      includes && !excludes
//    end

}
