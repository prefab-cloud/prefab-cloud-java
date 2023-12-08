package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

public class ConfigChangeEvent {

  private final String key;
  private final Optional<Prefab.Config> oldValue;
  private final Optional<Prefab.Config> newValue;

  public ConfigChangeEvent(
    String key,
    Optional<Prefab.Config> oldValue,
    Optional<Prefab.Config> newValue
  ) {
    this.key = key;
    this.oldValue = oldValue;
    this.newValue = newValue;
  }

  public String getKey() {
    return key;
  }

  public Optional<Prefab.Config> getOldValue() {
    return oldValue;
  }

  public Optional<Prefab.Config> getNewValue() {
    return newValue;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof ConfigChangeEvent) {
      final ConfigChangeEvent that = (ConfigChangeEvent) obj;
      return (
        Objects.equals(this.key, that.key) &&
        Objects.equals(this.oldValue, that.oldValue) &&
        Objects.equals(this.newValue, that.newValue)
      );
    }

    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, oldValue, newValue);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", "ConfigChangeEvent[", "]")
      .add("key='" + key + "'")
      .add("oldValue=" + oldValue)
      .add("newValue=" + newValue)
      .toString();
  }
}
