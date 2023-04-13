package cloud.prefab.context;

import cloud.prefab.domain.Prefab;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PrefabContext {

  private final String contextType;

  private final Optional<String> key;
  private final Map<String, Prefab.ConfigValue> properties;

  private PrefabContext(
    String contextType,
    Optional<String> key,
    Map<String, Prefab.ConfigValue> properties
  ) {
    this.contextType = contextType;
    this.key = key;
    this.properties = Map.copyOf(properties);
  }

  public String getContextType() {
    return contextType;
  }

  public Optional<String> getKey() {
    return key;
  }

  public Map<String, Prefab.ConfigValue> getProperties() {
    return properties;
  }

  public static class Builder {

    private final String contextType;
    private final Map<String, Prefab.ConfigValue> properties = new HashMap<>();
    private final Optional<String> key;

    private Builder(String contextType, Optional<String> key) {
      this.contextType = contextType;
      this.key = key;
    }

    public Builder set(String propertyName, String value) {
      properties.put(
        propertyName,
        Prefab.ConfigValue.newBuilder().setString(value).build()
      );
      return this;
    }

    public Builder set(String propertyName, boolean value) {
      properties.put(
        propertyName,
        Prefab.ConfigValue.newBuilder().setBool(value).build()
      );
      return this;
    }

    public Builder set(String propertyName, long value) {
      properties.put(propertyName, Prefab.ConfigValue.newBuilder().setInt(value).build());
      return this;
    }

    public Builder set(String propertyName, double value) {
      properties.put(
        propertyName,
        Prefab.ConfigValue.newBuilder().setDouble(value).build()
      );
      return this;
    }

    public PrefabContext build() {
      return new PrefabContext(contextType, key, properties);
    }
  }

  public static class KeyBuilder {

    private final String contextType;

    private KeyBuilder(String contextType) {
      this.contextType = contextType;
    }

    public Builder withKey(String key) {
      return new Builder(contextType, Optional.of(key));
    }

    public Builder withKey(Optional<String> key) {
      return new Builder(contextType, key);
    }

    public Builder withoutKey() {
      return new Builder(contextType, Optional.empty());
    }
  }

  public static KeyBuilder newBuilder(String contextType) {
    return new KeyBuilder(contextType);
  }
}
