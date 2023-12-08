package cloud.prefab.client.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractConfigStoreDeltaCalculator<
  VALUE_TYPE, LISTENER_EVENT_TYPE
> {

  public List<LISTENER_EVENT_TYPE> computeChangeEvents(
    Map<String, VALUE_TYPE> before,
    Map<String, VALUE_TYPE> after
  ) {
    MapDifference<String, VALUE_TYPE> delta = Maps.difference(before, after);
    if (delta.areEqual()) {
      return ImmutableList.of();
    } else {
      ImmutableList.Builder<LISTENER_EVENT_TYPE> changeEvents = ImmutableList.builder();

      // removed config values
      delta
        .entriesOnlyOnLeft()
        .forEach((key, value) ->
          changeEvents.add(createEvent(key, Optional.of(value), Optional.empty()))
        );

      // added config values
      delta
        .entriesOnlyOnRight()
        .forEach((key, value) ->
          changeEvents.add(createEvent(key, Optional.empty(), Optional.of(value)))
        );

      // changed config values
      delta
        .entriesDiffering()
        .forEach((key, values) ->
          changeEvents.add(
            createEvent(
              key,
              Optional.of(values.leftValue()),
              Optional.of(values.rightValue())
            )
          )
        );

      return changeEvents.build();
    }
  }

  abstract LISTENER_EVENT_TYPE createEvent(
    String name,
    Optional<VALUE_TYPE> oldValue,
    Optional<VALUE_TYPE> newValue
  );
}
