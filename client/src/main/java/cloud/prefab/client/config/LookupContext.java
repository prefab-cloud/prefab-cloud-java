package cloud.prefab.client.config;

import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class LookupContext {

  public static final LookupContext EMPTY = new LookupContext(
    Optional.empty(),
    PrefabContextSetReadable.EMPTY
  );

  private final Optional<Prefab.ConfigValue> namespaceMaybe;

  private final PrefabContextSetReadable prefabContextSetReadable;

  private Map<String, Prefab.ConfigValue> expandedProperties = null;

  public LookupContext(
    Optional<Prefab.ConfigValue> namespace,
    PrefabContextSetReadable prefabContextSetReadable
  ) {
    this.namespaceMaybe = namespace;
    this.prefabContextSetReadable = prefabContextSetReadable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LookupContext that = (LookupContext) o;
    return (
      Objects.equals(namespaceMaybe, that.namespaceMaybe) &&
      Objects.equals(prefabContextSetReadable, that.prefabContextSetReadable)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(namespaceMaybe, prefabContextSetReadable);
  }

  public Optional<Prefab.ConfigValue> getNamespace() {
    return namespaceMaybe;
  }

  public Map<String, Prefab.ConfigValue> getExpandedProperties() {
    if (this.expandedProperties == null) {
      int propertyCount =
        StreamSupport
          .stream(prefabContextSetReadable.getContexts().spliterator(), false)
          .mapToInt(context -> context.getProperties().size())
          .sum() +
        1;

      Map<String, Prefab.ConfigValue> expandedProperties = Maps.newHashMapWithExpectedSize(
        propertyCount
      );
      namespaceMaybe.ifPresent(namespace ->
        expandedProperties.put(ConfigResolver.NAMESPACE_KEY, namespace)
      );
      for (PrefabContext context : prefabContextSetReadable.getContexts()) {
        String prefix = context.getName().isBlank()
          ? ""
          : context.getName().toLowerCase() + ".";
        for (Map.Entry<String, Prefab.ConfigValue> stringConfigValueEntry : context
          .getProperties()
          .entrySet()) {
          expandedProperties.put(
            prefix + stringConfigValueEntry.getKey(),
            stringConfigValueEntry.getValue()
          );
        }
      }
      this.expandedProperties = ImmutableMap.copyOf(expandedProperties);
    }
    return this.expandedProperties;
  }
}
