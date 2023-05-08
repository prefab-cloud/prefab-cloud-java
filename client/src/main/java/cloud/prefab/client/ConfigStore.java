package cloud.prefab.client;

import cloud.prefab.client.config.ConfigElement;
import java.util.Collection;
import java.util.Iterator;

public interface ConfigStore {
  Collection<String> getKeys();

  ConfigElement getElement(String key);

  /**
   *
   * @return unmodifiable collection of all known config elements
   */
  Collection<ConfigElement> getElements();

  boolean containsKey(String key);
}
