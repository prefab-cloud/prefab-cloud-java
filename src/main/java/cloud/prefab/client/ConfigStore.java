package cloud.prefab.client;

import cloud.prefab.client.config.ConfigElement;
import java.util.Collection;

public interface ConfigStore {
  Collection<String> getKeys();

  ConfigElement getElement(String key);

  boolean containsKey(String key);
}
