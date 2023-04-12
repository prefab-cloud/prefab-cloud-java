package cloud.prefab.context;

import cloud.prefab.domain.Prefab;
import java.util.HashMap;
import java.util.Map;

public class PrefabContext {

  private final String contextType;

  private final String key;
  private final Map<String, Prefab.ConfigValue> properties;

  private PrefabContext(
    String contextType,
    String key,
    Map<String, Prefab.ConfigValue> properties
  ) {
    this.contextType = contextType;
    this.key = key;
    this.properties = Map.copyOf(properties);
  }

  public String getContextType() {
    return contextType;
  }

  public String getKey() {
    return key;
  }

  public Map<String, Prefab.ConfigValue> getProperties() {
    return properties;
  }

  public static class Builder {

    private final String contextType;
    private final Map<String, Prefab.ConfigValue> properties = new HashMap<>();
    private final String key;

    private Builder(String contextType, String key) {
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
      return new Builder(contextType, key);
    }
  }

  public static KeyBuilder newBuilder(String contextType) {
    return new KeyBuilder(contextType);
  }
}
