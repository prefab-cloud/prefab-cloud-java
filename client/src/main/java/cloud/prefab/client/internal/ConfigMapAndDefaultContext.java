package cloud.prefab.client.internal;

import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.context.PrefabContextSetReadable;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class ConfigMapAndDefaultContext {

  private final ImmutableMap<String, ConfigElement> configMap;

  private final PrefabContextSetReadable defaultContext;

  ConfigMapAndDefaultContext(
    Map<String, ConfigElement> configMap,
    PrefabContextSetReadable defaultContext
  ) {
    this.configMap = ImmutableMap.copyOf(configMap);
    this.defaultContext = defaultContext;
  }

  public ImmutableMap<String, ConfigElement> getConfigMap() {
    return configMap;
  }

  public PrefabContextSetReadable getDefaultContext() {
    return defaultContext;
  }
}
