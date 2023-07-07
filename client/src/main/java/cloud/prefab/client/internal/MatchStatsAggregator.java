package cloud.prefab.client.internal;

import cloud.prefab.client.config.Match;
import cloud.prefab.domain.Prefab;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MatchStatsAggregator {

  private StatsAggregate statsAggregate = new StatsAggregate();

  void setStatsAggregate(StatsAggregate statsAggregate) {
    this.statsAggregate = statsAggregate;
  }

  StatsAggregate getStatAggregate() {
    return statsAggregate;
  }

  void recordMatch(Match match, long timeStamp) {
    statsAggregate.recordMatch(match, timeStamp);
  }

  static class StatsAggregate {

    Map<String, Map<CountKey, Counter>> counterData = new HashMap<>();
    long minTime = 0;
    long maxTime = 0;

    long getMinTime() {
      return minTime;
    }

    long getMaxTime() {
      return maxTime;
    }

    Map<String, Map<CountKey, Counter>> getCounterData() {
      return counterData;
    }

    void recordMatch(Match match, long timeStamp) {
      if (minTime == 0 || timeStamp < minTime) {
        minTime = timeStamp;
      }
      if (timeStamp > maxTime) {
        maxTime = timeStamp;
      }

      Map<CountKey, Counter> innerMap = counterData.computeIfAbsent(
        match.getConfigElement().getConfig().getKey(),
        ignored -> new HashMap<>()
      );
      int selectedIndex = 0;
      for (Prefab.ConfigValue allowablevalue : match
        .getConfigElement()
        .getConfig()
        .getAllowableValuesList()) {
        if (allowablevalue.equals(match.getConfigValue())) {
          break;
        }
        selectedIndex++;
      }
      //FIXME can run off the end of the list

      CountKey countKey = new CountKey(
        match.getConfigElement().getConfig().getId(),
        selectedIndex
      );
      innerMap.computeIfAbsent(countKey, c -> new Counter()).inc();
    }
  }

  static class CountKey {

    long configId;
    long selectedIndex;

    CountKey(long configId, long selectedIndex) {
      this.configId = configId;
      this.selectedIndex = selectedIndex;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CountKey countKey = (CountKey) o;
      return configId == countKey.configId && selectedIndex == countKey.selectedIndex;
    }

    @Override
    public int hashCode() {
      return Objects.hash(configId, selectedIndex);
    }

    @Override
    public String toString() {
      return com.google.common.base.MoreObjects
        .toStringHelper(this)
        .add("configId", configId)
        .add("selectedIndex", selectedIndex)
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
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
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
