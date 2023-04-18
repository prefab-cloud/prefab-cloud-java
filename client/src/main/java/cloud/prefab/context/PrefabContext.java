package cloud.prefab.context;

import cloud.prefab.domain.Prefab;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PrefabContext implements PrefabContextSetReadable {

  private final String contextType;

  private final Map<String, Prefab.ConfigValue> properties;

  private PrefabContext(String contextType, Map<String, Prefab.ConfigValue> properties) {
    this.contextType = contextType;
    this.properties = Map.copyOf(properties);
  }

  public String getContextType() {
    return contextType;
  }

  public Map<String, Prefab.ConfigValue> getProperties() {
    return properties;
  }

  // implementation of PrefabContextSetReadable so one context is interchangable with many
  @Override
  public Optional<PrefabContext> getByType(String contextType) {
    if (contextType.equals(this.contextType)) {
      return Optional.of(this);
    }
    return Optional.empty();
  }

  @Override
  public Iterable<PrefabContext> getContexts() {
    return Collections.singleton(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PrefabContext that = (PrefabContext) o;
    return (
      Objects.equals(contextType, that.contextType) &&
      Objects.equals(properties, that.properties)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(contextType, properties);
  }

  public static PrefabContext fromMap(Map<String, Prefab.ConfigValue> configValueMap) {
    return new PrefabContext("", configValueMap);
  }

  public static class Builder {

    private final String contextType;
    private final Map<String, Prefab.ConfigValue> properties = new HashMap<>();

    private Builder(String contextType) {
      this.contextType = contextType;
    }

    public Builder put(String propertyName, String value) {
      properties.put(
        propertyName,
        Prefab.ConfigValue.newBuilder().setString(value).build()
      );
      return this;
    }

    public Builder put(String propertyName, boolean value) {
      properties.put(
        propertyName,
        Prefab.ConfigValue.newBuilder().setBool(value).build()
      );
      return this;
    }

    public Builder put(String propertyName, long value) {
      properties.put(propertyName, Prefab.ConfigValue.newBuilder().setInt(value).build());
      return this;
    }

    public Builder put(String propertyName, double value) {
      properties.put(
        propertyName,
        Prefab.ConfigValue.newBuilder().setDouble(value).build()
      );
      return this;
    }

    public PrefabContext build() {
      return new PrefabContext(contextType, properties);
    }
  }

  public static Builder newBuilder(String contextType) {
    return new Builder(contextType);
  }
}
