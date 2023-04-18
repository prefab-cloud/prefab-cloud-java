package cloud.prefab.client.internal;

import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import java.util.Optional;

public class ThreadLocalContextStore implements ContextStore {

  static final ThreadLocal<PrefabContextSet> PREFAB_CONTEXT_SET_THREAD_LOCAL = new ThreadLocal<>();
  public static final ThreadLocalContextStore INSTANCE = new ThreadLocalContextStore();

  private ThreadLocalContextStore() {}

  @Override
  public void addContext(PrefabContext prefabContext) {
    PrefabContextSet prefabContextSet = PREFAB_CONTEXT_SET_THREAD_LOCAL.get();

    if (PREFAB_CONTEXT_SET_THREAD_LOCAL.get() == null) {
      prefabContextSet = new PrefabContextSet();
      PREFAB_CONTEXT_SET_THREAD_LOCAL.set(prefabContextSet);
    }
    prefabContextSet.addContext(prefabContext);
  }

  @Override
  public void clearContexts() {
    PREFAB_CONTEXT_SET_THREAD_LOCAL.remove();
  }

  @Override
  public Optional<PrefabContextSetReadable> getContexts() {
    return Optional.ofNullable(PREFAB_CONTEXT_SET_THREAD_LOCAL.get());
  }
}
