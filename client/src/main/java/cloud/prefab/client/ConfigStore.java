package cloud.prefab.client;

import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.context.PrefabContextSetReadable;
import java.util.Collection;

public interface ConfigStore {
  Collection<String> getKeys();

  ConfigElement getElement(String key);

  /**
   *
   * @return unmodifiable collection of all known config elements
   */
  Collection<ConfigElement> getElements();

  boolean containsKey(String key);

  long getProjectEnvironmentId();

  /**
   *
   * @return the context sent from prefab - included with the config payload
   */
  PrefabContextSetReadable getConfigIncludedContext();

  /**
   *
   * @return the context set in options before starting the prefab client
   */
  PrefabContextSetReadable getGlobalContext();
}
