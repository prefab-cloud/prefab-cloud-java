package cloud.prefab.client.config;

import cloud.prefab.client.util.RandomProvider;
import cloud.prefab.client.util.RandomProviderIF;
import cloud.prefab.domain.Prefab;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.util.List;
import java.util.Optional;

public class WeightedValueEvaluator {

  private static final HashFunction hash = Hashing.murmur3_32();
  private static final long UNSIGNED_INT_MAX =
    Integer.MAX_VALUE + (long) Integer.MAX_VALUE;

  private RandomProviderIF randomProvider = new RandomProvider();

  public Prefab.ConfigValue toValue(
    Prefab.WeightedValues weightedValues,
    String key,
    Optional<String> lookupKey
  ) {
    double pctThroughDistribution = randomProvider.random();
    if (lookupKey.isPresent()) {
      pctThroughDistribution = getUserPct(lookupKey.get(), key);
    }
    return getVariantIdxFromWeights(
      weightedValues.getWeightedValuesList(),
      pctThroughDistribution
    );
  }

  Prefab.ConfigValue getVariantIdxFromWeights(
    List<Prefab.WeightedValue> weightedValues,
    double pctThroughDistribution
  ) {
    Optional<Integer> distributionSpace = weightedValues
      .stream()
      .map(Prefab.WeightedValue::getWeight)
      .reduce(Integer::sum);
    int bucket = (int) (distributionSpace.get() * pctThroughDistribution);
    int sum = 0;
    for (Prefab.WeightedValue weightedValue : weightedValues) {
      if (bucket < sum + weightedValue.getWeight()) {
        return weightedValue.getValue();
      } else {
        sum += weightedValue.getWeight();
      }
    }
    // variants didn't add up to 100%
    return weightedValues.get(0).getValue();
  }

  double getUserPct(String lookupKey, String featureName) {
    final String toHash = String.format("%s%s", featureName, lookupKey);
    final HashCode hashCode = hash.hashBytes(toHash.getBytes());
    return pct(hashCode.asInt());
  }

  private double pct(int signedInt) {
    long y = signedInt & 0x00000000ffffffffL;
    return y / (double) (UNSIGNED_INT_MAX);
  }
}
