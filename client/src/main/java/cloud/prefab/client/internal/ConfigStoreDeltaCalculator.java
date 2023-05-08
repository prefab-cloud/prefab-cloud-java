package cloud.prefab.client.internal;

import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConfigStoreDeltaCalculator {

  public List<ConfigChangeEvent> computeChangeEvents(
    Map<String, Optional<Prefab.ConfigValue>> before,
    Map<String, Optional<Prefab.ConfigValue>> after
  ) {
    MapDifference<String, Optional<Prefab.ConfigValue>> delta = Maps.difference(
      before,
      after
    );
    if (delta.areEqual()) {
      return ImmutableList.of();
    } else {
      ImmutableList.Builder<ConfigChangeEvent> changeEvents = ImmutableList.builder();

      // removed config values
      delta
        .entriesOnlyOnLeft()
        .forEach((key, value) ->
          changeEvents.add(new ConfigChangeEvent(key, value, Optional.empty()))
        );

      // added config values
      delta
        .entriesOnlyOnRight()
        .forEach((key, value) ->
          changeEvents.add(new ConfigChangeEvent(key, Optional.empty(), value))
        );

      // changed config values
      delta
        .entriesDiffering()
        .forEach((key, values) ->
          changeEvents.add(
            new ConfigChangeEvent(key, values.leftValue(), values.rightValue())
          )
        );

      return changeEvents.build();
    }
  }
}
