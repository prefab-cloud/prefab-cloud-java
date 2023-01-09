package cloud.prefab.client;

import cloud.prefab.domain.Prefab;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ConfigStore {
  Optional<Prefab.ConfigValue> get(String key);

  Optional<Prefab.ConfigValue> get(
    String key,
    Map<String, Prefab.ConfigValue> properties
  );

  Collection<String> getKeys();
}
