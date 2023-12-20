package cloud.prefab.client.internal;

import cloud.prefab.client.config.ConfigElement;
import java.util.Map;

public class MergedConfigData {

  private final Map<String, ConfigElement> configs;
  private final long envId;
  private final ContextWrapper baseContextWrapper;
  private final ContextWrapper configIncludedContext;

  MergedConfigData(
    Map<String, ConfigElement> configs,
    long envId,
    ContextWrapper baseContextWrapper,
    ContextWrapper configIncludedContext
  ) {
    this.configs = configs;
    this.envId = envId;
    this.baseContextWrapper = baseContextWrapper;
    this.configIncludedContext = configIncludedContext;
  }

  public Map<String, ConfigElement> getConfigs() {
    return configs;
  }

  public ContextWrapper getConfigIncludedContext() {
    return configIncludedContext;
  }

  public long getEnvId() {
    return envId;
  }

  public ContextWrapper getBaseContextWrapper() {
    return baseContextWrapper;
  }
}
