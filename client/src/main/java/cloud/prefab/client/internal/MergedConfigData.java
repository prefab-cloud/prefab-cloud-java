package cloud.prefab.client.internal;

import cloud.prefab.client.config.ConfigElement;
import java.util.Map;

public class MergedConfigData {

  private final Map<String, ConfigElement> configs;
  private final long envId;
  private final ContextWrapper globalContextWrapper;
  private final ContextWrapper configIncludedContext;

  MergedConfigData(
    Map<String, ConfigElement> configs,
    long envId,
    ContextWrapper globalContextWrapper,
    ContextWrapper configIncludedContext
  ) {
    this.configs = configs;
    this.envId = envId;
    this.globalContextWrapper = globalContextWrapper;
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

  public ContextWrapper getGlobalContextWrapper() {
    return globalContextWrapper;
  }
}
