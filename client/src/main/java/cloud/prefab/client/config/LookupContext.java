package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class LookupContext {

  public static final LookupContext EMPTY = new LookupContext(
    Optional.empty(),
    Optional.empty(),
    Optional.empty(),
    Collections.emptyMap()
  );

  private final Optional<String> contextKey;
  private final Optional<Prefab.ConfigValue> namespaceMaybe;

  private final Map<String, Prefab.ConfigValue> properties;
  private final Optional<String> contextTypeMaybe;

  private Map<String, Prefab.ConfigValue> expandedProperties = null;

  public LookupContext(
    Optional<String> contextType,
    Optional<String> contextKey,
    Optional<Prefab.ConfigValue> namespace,
    Map<String, Prefab.ConfigValue> properties
  ) {
    this.contextTypeMaybe = contextType.map(String::toLowerCase);
    this.contextKey = contextKey;
    this.namespaceMaybe = namespace;
    this.properties = properties;
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
      Objects.equals(contextKey, that.contextKey) &&
      Objects.equals(namespaceMaybe, that.namespaceMaybe) &&
      Objects.equals(properties, that.properties)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(contextKey, namespaceMaybe, properties);
  }

  public Map<String, Prefab.ConfigValue> getProperties() {
    return properties;
  }

  public Optional<String> getContextKey() {
    return contextKey;
  }

  public Optional<Prefab.ConfigValue> getNamespace() {
    return namespaceMaybe;
  }

  public Map<String, Prefab.ConfigValue> getExpandedProperties() {
    if (expandedProperties == null) {
      Map<String, Prefab.ConfigValue> newMap = Maps.newHashMapWithExpectedSize(
        namespaceMaybe.map(ignored -> 1).orElse(0) +
        contextKey.map(ignored -> 1).orElse(0) +
        properties.size()
      );
      namespaceMaybe.ifPresent(namespace ->
        newMap.put(ConfigResolver.NAMESPACE_KEY, namespace)
      );
      contextKey.ifPresent(c ->
        newMap.put(
          ConfigResolver.LOOKUP_KEY,
          Prefab.ConfigValue.newBuilder().setString(c).build()
        )
      );
      String prefix = contextTypeMaybe
        .filter(Predicate.not(String::isEmpty))
        .map(contextType -> contextType + ".")
        .orElse("");
      for (Map.Entry<String, Prefab.ConfigValue> keyValueEntry : properties.entrySet()) {
        newMap.put(prefix + keyValueEntry.getKey(), keyValueEntry.getValue());
      }
      expandedProperties = ImmutableMap.copyOf(newMap);
    }
    return expandedProperties;
  }
}
