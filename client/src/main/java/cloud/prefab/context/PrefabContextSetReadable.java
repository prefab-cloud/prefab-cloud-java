package cloud.prefab.context;

import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public interface PrefabContextSetReadable {
  Optional<PrefabContext> getByName(String contextName);
  Iterable<PrefabContext> getContexts();

  boolean isEmpty();

  default Map<String, Prefab.ConfigValue> flattenToImmutableMap() {
    return Streams
      .stream(getContexts())
      .sorted(Comparator.comparing(PrefabContext::getName))
      .flatMap(context ->
        context
          .getNameQualifiedProperties()
          .entrySet()
          .stream()
          .sorted(Map.Entry.comparingByKey())
      )
      .collect(
        ImmutableMap.toImmutableMap(
          Map.Entry::getKey, // Key mapper
          Map.Entry::getValue, // Value mapper
          (existingValue, newValue) -> newValue
        )
      );
  }

  PrefabContextSetReadable EMPTY = new PrefabContextSetReadable() {
    @Override
    public Optional<PrefabContext> getByName(String contextName) {
      return Optional.empty();
    }

    @Override
    public Iterable<PrefabContext> getContexts() {
      return Collections.emptyList();
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public String toString() {
      return com.google.common.base.MoreObjects
        .toStringHelper(this)
        .add("contextByNameMap", "EMPTY")
        .toString();
    }
  };

  static PrefabContextSetReadable readOnlyContextSetView(PrefabContextSet contextSet) {
    return new PrefabContextSetReadable() {
      @Override
      public Optional<PrefabContext> getByName(String contextName) {
        return contextSet.getByName(contextName);
      }

      @Override
      public Iterable<PrefabContext> getContexts() {
        return contextSet.getContexts();
      }

      @Override
      public boolean isEmpty() {
        return contextSet.isEmpty();
      }

      @Override
      public String toString() {
        return contextSet.toString();
      }
    };
  }

  default String getFingerPrint() {
    return StreamSupport
      .stream(getContexts().spliterator(), false)
      .filter(c -> !c.getName().isBlank())
      .filter(c -> c.getProperties().containsKey("key"))
      .sorted(Comparator.comparing(PrefabContext::getName))
      .map(c ->
        new StringBuilder()
          .append(c.getName())
          .append("--")
          .append(c.getProperties().get("key").toString().trim())
      )
      .collect(Collectors.joining());
  }
}
