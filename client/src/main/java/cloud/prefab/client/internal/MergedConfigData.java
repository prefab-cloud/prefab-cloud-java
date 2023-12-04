package cloud.prefab.client.internal;

import cloud.prefab.client.config.ConfigElement;
import java.util.Map;

public class MergedConfigData {

  private final Map<String, ConfigElement> configs;
  private final long envId;
  private final DefaultContextWrapper defaultContextWrapper;

  MergedConfigData(
    Map<String, ConfigElement> configs,
    long envId,
    DefaultContextWrapper defaultContextWrapper
  ) {
    this.configs = configs;
    this.envId = envId;
    this.defaultContextWrapper = defaultContextWrapper;
  }

  public Map<String, ConfigElement> getConfigs() {
    return configs;
  }

  public DefaultContextWrapper getDefaultContextWrapper() {
    return defaultContextWrapper;
  }

  public long getEnvId() {
    return envId;
  }
}
