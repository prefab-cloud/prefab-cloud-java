package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TestUtils {

  public static Prefab.ConfigValue getStringConfigValue(String s) {
    return Prefab.ConfigValue.newBuilder().setString(s).build();
  }

  public static Prefab.ConfigValue getIntConfigValue(long value) {
    return Prefab.ConfigValue.newBuilder().setInt(value).build();
  }

  // note this ignores the ConfigValue in selected value which in practice will be caught by assertj when doing comparison
  public static final Comparator<Prefab.ConfigEvaluationCounter> CONFIG_EVALUATION_COUNTER_COMPARATOR = (
      o1,
      o2
    ) ->
    Comparator
      .comparingLong(Prefab.ConfigEvaluationCounter::getConfigId)
      .thenComparingLong(Prefab.ConfigEvaluationCounter::getConfigRowIndex)
      .thenComparingLong(Prefab.ConfigEvaluationCounter::getConditionalValueIndex)
      .thenComparingLong(Prefab.ConfigEvaluationCounter::getSelectedIndex)
      .thenComparingLong(Prefab.ConfigEvaluationCounter::getCount)
      .compare(o1, o2);

  public static Prefab.ConfigEvaluationSummary normalizeCounterOrder(
    Prefab.ConfigEvaluationSummary configEvaluationSummary
  ) {
    List<Prefab.ConfigEvaluationCounter> sortedCounts = configEvaluationSummary
      .getCountersList()
      .stream()
      .sorted(CONFIG_EVALUATION_COUNTER_COMPARATOR)
      .collect(Collectors.toList());
    return configEvaluationSummary
      .toBuilder()
      .clearCounters()
      .addAllCounters(sortedCounts)
      .build();
  }
}
