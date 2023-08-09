package cloud.prefab.context;

import cloud.prefab.domain.Prefab;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PrefabContext implements PrefabContextSetReadable {

  private final String name;

  private final Map<String, Prefab.ConfigValue> properties;

  private PrefabContext(String name, Map<String, Prefab.ConfigValue> properties) {
    this.name = name;
    this.properties = Map.copyOf(properties);
  }

  public String getName() {
    return name;
  }

  @Override
  public boolean isEmpty() {
    return properties.isEmpty();
  }

  public Map<String, Prefab.ConfigValue> getProperties() {
    return properties;
  }

  // implementation of PrefabContextSetReadable so one context is interchangable with many
  @Override
  public Optional<PrefabContext> getByName(String contextType) {
    if (contextType.equals(this.name)) {
      return Optional.of(this);
    }
    return Optional.empty();
  }

  @Override
  public Iterable<PrefabContext> getContexts() {
    return Collections.singleton(this);
  }

  public Prefab.Context toProtoContext() {
    return Prefab.Context
      .newBuilder()
      .setType(getName())
      .putAllValues(getProperties())
      .build();
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
      Objects.equals(name, that.name) && Objects.equals(properties, that.properties)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, properties);
  }

  @Override
  public String toString() {
    return com.google.common.base.MoreObjects
      .toStringHelper(this)
      .add("name", name)
      .add("properties", properties)
      .toString();
  }

  public Prefab.ContextShape getShape() {
    Prefab.ContextShape.Builder shapeBuilder = Prefab.ContextShape
      .newBuilder()
      .setName(getName());
    properties.forEach((key, value) ->
      shapeBuilder.putFieldTypes(key, value.getTypeCase().getNumber())
    );
    return shapeBuilder.build();
  }

  public static PrefabContext unnamedFromMap(
    Map<String, Prefab.ConfigValue> configValueMap
  ) {
    return new PrefabContext("", configValueMap);
  }

  public static PrefabContext fromMap(
    String name,
    Map<String, Prefab.ConfigValue> configValueMap
  ) {
    return new PrefabContext(name, configValueMap);
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
