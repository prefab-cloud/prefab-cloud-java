package cloud.prefab.client.internal;

import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.util.RandomProvider;
import cloud.prefab.client.util.RandomProviderIF;
import cloud.prefab.domain.Prefab;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Optional;

public class WeightedValueEvaluator {

  private final HashProvider hashProvider;

  private final RandomProviderIF randomProvider;

  public WeightedValueEvaluator() {
    this(new RandomProvider(), HashProvider.DEFAULT);
  }

  @VisibleForTesting
  WeightedValueEvaluator(RandomProviderIF randomProvider, HashProvider hashProvider) {
    this.randomProvider = randomProvider;
    this.hashProvider = hashProvider;
  }

  public Prefab.ConfigValue toValue(
    Prefab.WeightedValues weightedValues,
    String featureName,
    LookupContext lookupContext
  ) {
    return toResult(weightedValues, featureName, lookupContext).getValue();
  }

  public Result toResult(
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
    return getValueFromWeightsAndPercent(
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

  private Result getValueFromWeightsAndPercent(
    List<Prefab.WeightedValue> weightedValues,
    double targetPctThroughDistribution
  ) {
    int distributionSpace = weightedValues
      .stream()
      .map(Prefab.WeightedValue::getWeight)
      .reduce(Integer::sum)
      .orElse(1);
    int sum = 0;
    int index = 0;
    for (Prefab.WeightedValue weightedValue : weightedValues) {
      sum += weightedValue.getWeight();
      double percentThroughDistribution = sum / (double) distributionSpace;
      if (targetPctThroughDistribution <= percentThroughDistribution) {
        return Result.of(weightedValue.getValue(), index);
      }
      index++;
    }
    // variants didn't add up to 100%
    return Result.of(weightedValues.get(0).getValue(), 0);
  }

  private double getUserPct(String featureName, String configValue) {
    final String toHash = featureName + configValue;
    return hashProvider.hash(toHash);
  }

  public static class Result {

    Prefab.ConfigValue value;
    int index;

    Result(Prefab.ConfigValue value, int index) {
      this.value = value;
      this.index = index;
    }

    public static Result of(Prefab.ConfigValue value, int index) {
      return new Result(value, index);
    }

    public Prefab.ConfigValue getValue() {
      return value;
    }

    public int getIndex() {
      return index;
    }
  }
}
