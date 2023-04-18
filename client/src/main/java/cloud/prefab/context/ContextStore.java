package cloud.prefab.context;

import java.util.Optional;

public interface ContextStore {
  /**
   * Adds a context to the set of contexts for the current context-session scope
   * If there's already a context with the same type stored in the scope,
   * it is overwritten
   * @param prefabContext a context -
   */
  void addContext(PrefabContext prefabContext);

  /**
   * Removes all prefab contexts the current context session scope
   * (By default, this is stored in a ThreadLocal)
   */
  void clearContexts();

  /**
   *
   * @return unmodifiable PrefabContextSetReadable view
   */
  Optional<PrefabContextSetReadable> getContexts();
}
