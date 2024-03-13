package cloud.prefab.client.internal;

import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.context.PrefabContextSetReadable;
import java.util.Map;

public class MergedConfigData {

  private final Map<String, ConfigElement> configs;
  private final long envId;
  private final PrefabContextSetReadable globalContextSet;
  private final PrefabContextSetReadable configIncludedContextSet;

  MergedConfigData(
    Map<String, ConfigElement> configs,
    long envId,
    PrefabContextSetReadable globalContextSet,
    PrefabContextSetReadable configIncludedContextSet
  ) {
    this.configs = configs;
    this.envId = envId;
    this.globalContextSet = globalContextSet;
    this.configIncludedContextSet = configIncludedContextSet;
  }

  public Map<String, ConfigElement> getConfigs() {
    return configs;
  }

  public PrefabContextSetReadable getConfigIncludedContext() {
    return configIncludedContextSet;
  }

  public long getEnvId() {
    return envId;
  }

  public PrefabContextSetReadable getGlobalContextSet() {
    return globalContextSet;
  }
}
