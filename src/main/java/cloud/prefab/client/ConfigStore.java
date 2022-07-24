package cloud.prefab.client;

import cloud.prefab.domain.Prefab;
import java.util.Collection;
import java.util.Optional;

public interface ConfigStore {
  Optional<Prefab.ConfigValue> get(String key);

  Optional<Prefab.Config> getConfigObj(String key);

  long getProjectId();

  Collection<String> getKeys();
}
