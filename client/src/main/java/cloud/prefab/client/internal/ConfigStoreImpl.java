package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigStore;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.context.PrefabContextSetReadable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigStoreImpl implements ConfigStore {

  private final AtomicReference<MergedConfigData> data = new AtomicReference<>(
    new MergedConfigData(
      Map.of(),
      0,
      PrefabContextSetReadable.EMPTY,
      PrefabContextSetReadable.EMPTY
    )
  );

  @Override
  public Collection<String> getKeys() {
    return data.get().getConfigs().keySet();
  }

  public Set<Map.Entry<String, ConfigElement>> entrySet() {
    return data.get().getConfigs().entrySet();
  }

  @Override
  public Collection<ConfigElement> getElements() {
    return data.get().getConfigs().values();
  }

  public void set(MergedConfigData mergedConfigData) {
    data.set(mergedConfigData);
  }

  @Override
  public ConfigElement getElement(String key) {
    return data.get().getConfigs().get(key);
  }

  @Override
  public boolean containsKey(String key) {
    return data.get().getConfigs().containsKey(key);
  }

  @Override
  public long getProjectEnvironmentId() {
    return data.get().getEnvId();
  }

  @Override
  public PrefabContextSetReadable getConfigIncludedContext() {
    return data.get().getConfigIncludedContext();
  }

  @Override
  public PrefabContextSetReadable getGlobalContext() {
    return data.get().getGlobalContextSet();
  }
}
