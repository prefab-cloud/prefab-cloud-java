package cloud.prefab.client.internal;

import cloud.prefab.client.config.Match;
import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class MatchStatsAggregator {

  private static final Set<Prefab.ConfigType> SUPPORTED_CONFIG_TYPES = Sets.immutableEnumSet(
    Prefab.ConfigType.CONFIG,
    Prefab.ConfigType.FEATURE_FLAG
  );
  private StatsAggregate statsAggregate = new StatsAggregate();

  void setStatsAggregate(StatsAggregate statsAggregate) {
    this.statsAggregate = statsAggregate;
  }

  StatsAggregate getStatsAggregate() {
    return statsAggregate;
  }

  StatsAggregate getAndResetStatsAggregate() {
    StatsAggregate currentStatsAggregate = statsAggregate;
    statsAggregate = new StatsAggregate();
    return currentStatsAggregate;
  }

  void recordMatch(Match match, long timeStamp) {
    if (
      SUPPORTED_CONFIG_TYPES.contains(
        match.getConfigElement().getConfig().getConfigType()
      )
    ) {
      statsAggregate.recordMatch(match, timeStamp);
    }
  }

  static class StatsAggregate {

    Map<ConfigKeyAndTypeKey, Map<CountKey, Counter>> counterData = new HashMap<>();
    long minTime = 0;
    long maxTime = 0;

    long getMinTime() {
      return minTime;
    }

    long getMaxTime() {
      return maxTime;
    }

    Map<ConfigKeyAndTypeKey, Map<CountKey, Counter>> getCounterData() {
      return counterData;
    }

    void recordMatch(Match match, long timeStamp) {
      if (minTime == 0 || timeStamp < minTime) {
        minTime = timeStamp;
      }
      if (timeStamp > maxTime) {
        maxTime = timeStamp;
      }

      ConfigKeyAndTypeKey configKeyAndTypeKey = new ConfigKeyAndTypeKey(
        match.getConfigElement().getConfig().getKey(),
        match.getConfigElement().getConfig().getConfigType()
      );

      Map<CountKey, Counter> innerMap = counterData.computeIfAbsent(
        configKeyAndTypeKey,
        ignored -> new HashMap<>()
      );

      CountKey countKey = new CountKey(
        match.getConfigElement().getConfig().getId(),
        match.getConfigValue(),
        indexOfMatch(
          match.getConfigValue(),
          match.getConfigElement().getConfig().getAllowableValuesList()
        ),
        match.getRowIndex(),
        match.getConditionalValueIndex(),
        match.getWeightedValueIndex()
      );
      innerMap.computeIfAbsent(countKey, c -> new Counter(0)).inc();
    }

    private int indexOfMatch(
      Prefab.ConfigValue configValue,
      List<Prefab.ConfigValue> allowableValuesList
    ) {
      int selectedIndex = 0;
      for (Prefab.ConfigValue value : allowableValuesList) {
        if (value.equals(configValue)) {
          return selectedIndex;
        }
        selectedIndex++;
      }
      return -1;
    }

    Prefab.ConfigEvaluationSummaries toProto() {
      Prefab.ConfigEvaluationSummaries.Builder builder = Prefab.ConfigEvaluationSummaries.newBuilder();
      builder.setStart(getMinTime());
      builder.setEnd(getMaxTime());
      for (Map.Entry<ConfigKeyAndTypeKey, Map<CountKey, Counter>> mapEntry : counterData.entrySet()) {
        Prefab.ConfigEvaluationSummary.Builder summaryBuilder = Prefab.ConfigEvaluationSummary
          .newBuilder()
          .setType(mapEntry.getKey().configType)
          .setKey(mapEntry.getKey().key);
        for (Map.Entry<CountKey, Counter> countKeyCounterEntry : mapEntry
          .getValue()
          .entrySet()) {
          CountKey countKey = countKeyCounterEntry.getKey();
          Counter counter = countKeyCounterEntry.getValue();
          Prefab.ConfigEvaluationCounter.Builder counterProtoBuilder = Prefab.ConfigEvaluationCounter
            .newBuilder()
            .setConfigId(countKey.configId)
            .setCount(counter.count);
          if (countKey.selectedIndex >= 0) {
            counterProtoBuilder.setSelectedIndex(countKey.selectedIndex);
          }
          counterProtoBuilder.setConfigRowIndex(countKey.rowIndex);
          counterProtoBuilder.setConditionalValueIndex(countKey.valueIndex);
          countKey.weightedValueIndex.ifPresent(
            counterProtoBuilder::setWeightedValueIndex
          );
          counterProtoBuilder.setSelectedValue(countKey.configValue);
          summaryBuilder.addCounters(counterProtoBuilder);
        }
        builder.addSummaries(summaryBuilder.build());
      }
      return builder.build();
    }
  }

  static class ConfigKeyAndTypeKey {

    final String key;
    final Prefab.ConfigType configType;

    public ConfigKeyAndTypeKey(String key, Prefab.ConfigType configType) {
      this.key = key;
      this.configType = configType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ConfigKeyAndTypeKey that = (ConfigKeyAndTypeKey) o;
      return Objects.equals(key, that.key) && configType == that.configType;
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, configType);
    }

    @Override
    public String toString() {
      return MoreObjects
        .toStringHelper(this)
        .add("key", key)
        .add("configType", configType)
        .toString();
    }
  }

  static class CountKey {

    final long configId;
    final int selectedIndex;
    private final int rowIndex;
    private final int valueIndex;
    private final Optional<Integer> weightedValueIndex;
    final Prefab.ConfigValue configValue;

    CountKey(
      long configId,
      Prefab.ConfigValue configValue,
      int selectedIndex,
      int rowIndex,
      int valueIndex,
      Optional<Integer> weightedValueIndex
    ) {
      this.configId = configId;
      this.selectedIndex = selectedIndex;
      this.rowIndex = rowIndex;
      this.valueIndex = valueIndex;
      this.weightedValueIndex = weightedValueIndex;
      this.configValue = configValue;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CountKey countKey = (CountKey) o;
      return (
        configId == countKey.configId &&
        selectedIndex == countKey.selectedIndex &&
        rowIndex == countKey.rowIndex &&
        valueIndex == countKey.valueIndex &&
        Objects.equals(weightedValueIndex, countKey.weightedValueIndex) &&
        Objects.equals(configValue, countKey.configValue)
      );
    }

    @Override
    public int hashCode() {
      return Objects.hash(
        configId,
        selectedIndex,
        rowIndex,
        valueIndex,
        weightedValueIndex,
        configValue
      );
    }

    @Override
    public String toString() {
      return MoreObjects
        .toStringHelper(this)
        .add("configId", configId)
        .add("selectedIndex", selectedIndex)
        .add("rowIndex", rowIndex)
        .add("valueIndex", valueIndex)
        .add("weightedValueIndex", weightedValueIndex)
        .add("configValue", configValue)
        .toString();
    }
  }

  static class Counter {

    long count;

    void inc() {
      count += 1;
    }

    Counter() {
      this(0);
    }

    Counter(long count) {
      this.count = count;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Counter counter = (Counter) o;
      return count == counter.count;
    }

    @Override
    public int hashCode() {
      return Objects.hash(count);
    }

    @Override
    public String toString() {
      return com.google.common.base.MoreObjects
        .toStringHelper(this)
        .add("count", count)
        .toString();
    }
  }
}
