package cloud.prefab.client.config.logging;

import cloud.prefab.client.config.ConfigChangeEvent;

public interface LogLevelChangeListener {
  void onChange(LogLevelChangeEvent changeEvent);
}
