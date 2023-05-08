package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigStore;
import cloud.prefab.client.config.ConfigElement;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigStoreImpl implements ConfigStore {

  private final AtomicReference<ImmutableMap<String, ConfigElement>> localMap = new AtomicReference<>(
    ImmutableMap.of()
  );

  @Override
  public Collection<String> getKeys() {
    return localMap.get().keySet();
  }

  public ImmutableSet<Map.Entry<String, ConfigElement>> entrySet() {
    return localMap.get().entrySet();
  }

  @Override
  public Collection<ConfigElement> getElements() {
    return localMap.get().values();
  }

  public void set(ImmutableMap<String, ConfigElement> newData) {
    localMap.set(newData);
  }

  @Override
  public ConfigElement getElement(String key) {
    return localMap.get().get(key);
  }

  @Override
  public boolean containsKey(String key) {
    return localMap.get().containsKey(key);
  }
}
