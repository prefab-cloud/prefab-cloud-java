package cloud.prefab.client.config;

import cloud.prefab.client.util.RandomProvider;
import cloud.prefab.client.util.RandomProviderIF;
import cloud.prefab.domain.Prefab;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.math.LongMath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class WeightedValueEvaluator {

  public static final long UNSIGNED_INT_MAX =
    Integer.MAX_VALUE + (long) Integer.MAX_VALUE;
  private final HashFunction hashFunction;

  private final RandomProviderIF randomProvider;

  public WeightedValueEvaluator() {
    this(new RandomProvider(), Hashing.murmur3_32());
  }

  @VisibleForTesting
  WeightedValueEvaluator(RandomProviderIF randomProvider, HashFunction hashFunction) {
    this.randomProvider = randomProvider;
    this.hashFunction = hashFunction;
  }

  public Prefab.ConfigValue toValue(
    Prefab.WeightedValues weightedValues,
    String featureName,
    LookupContext lookupContext
  ) {
    Optional<String> hashPropertyValue = getHashPropertyValue(
      weightedValues,
      lookupContext
    )
      .flatMap(ConfigValueUtils::coerceToString);

    double pctThroughDistribution = hashPropertyValue
      .map(s -> getUserPct(featureName, s))
      .orElseGet(randomProvider::random);
    return getVariantIdxFromWeights(
      weightedValues.getWeightedValuesList(),
      pctThroughDistribution
    );
  }

  private Optional<Prefab.ConfigValue> getHashPropertyValue(
    Prefab.WeightedValues weightedValues,
    LookupContext lookupContext
  ) {
    if (weightedValues.hasHashByPropertyName()) {
      return lookupContext.getValue(weightedValues.getHashByPropertyName());
    }

    return Optional.empty();
  }

  private Prefab.ConfigValue getVariantIdxFromWeights(
    List<Prefab.WeightedValue> weightedValues,
    double targetPctThroughDistribution
  ) {
    int distributionSpace = weightedValues
      .stream()
      .map(Prefab.WeightedValue::getWeight)
      .reduce(Integer::sum)
      .orElse(1);
    int sum = 0;
    for (Prefab.WeightedValue weightedValue : weightedValues) {
      sum += weightedValue.getWeight();
      double percentThroughDistribution = sum / (double) distributionSpace;
      if (targetPctThroughDistribution <= percentThroughDistribution) {
        return weightedValue.getValue();
      }
    }
    // variants didn't add up to 100%
    return weightedValues.get(0).getValue();
  }

  private double getUserPct(String featureName, String configValue) {
    final String toHash = featureName + configValue;
    final HashCode hashCode = hashFunction.hashString(toHash, StandardCharsets.UTF_8);
    return pct(hashCode.asInt());
  }

  private double pct(int signedInt) {
    long y = (long) signedInt + Integer.MAX_VALUE;
    return y / (double) (UNSIGNED_INT_MAX);
  }
}
