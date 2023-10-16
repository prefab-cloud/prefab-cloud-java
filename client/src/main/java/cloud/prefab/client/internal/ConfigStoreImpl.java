package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigStore;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigStoreImpl implements ConfigStore {

  private final AtomicReference<ConfigMapAndDefaultContext> localMap = new AtomicReference<>(
    new ConfigMapAndDefaultContext(ImmutableMap.of(), PrefabContextSet.EMPTY)
  );

  @Override
  public Collection<String> getKeys() {
    return localMap.get().getConfigMap().keySet();
  }

  public ImmutableSet<Map.Entry<String, ConfigElement>> entrySet() {
    return localMap.get().getConfigMap().entrySet();
  }

  @Override
  public Collection<ConfigElement> getElements() {
    return localMap.get().getConfigMap().values();
  }

  public void set(ConfigMapAndDefaultContext configMapAndDefaultContext) {
    localMap.set(configMapAndDefaultContext);
  }

  @Override
  public ConfigElement getElement(String key) {
    return localMap.get().getConfigMap().get(key);
  }

  @Override
  public boolean containsKey(String key) {
    return localMap.get().getConfigMap().containsKey(key);
  }

  @Override
  public PrefabContextSetReadable getDefaultContext() {
    return localMap.get().getDefaultContext();
  }
}
