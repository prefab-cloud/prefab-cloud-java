package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import java.util.Map;

public interface ConfigChangeListener {
  void prefabConfigUpdateCallback(Map<String, Prefab.ConfigValue> changes);
}
