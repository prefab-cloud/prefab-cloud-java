package cloud.prefab.client.internal;

import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import java.util.Optional;

public class ThreadLocalContextStore implements ContextStore {

  static final ThreadLocal<PrefabContextSet> PREFAB_CONTEXT_SET_THREAD_LOCAL = ThreadLocal.withInitial(
    PrefabContextSet::new
  );
  public static final ThreadLocalContextStore INSTANCE = new ThreadLocalContextStore();

  private ThreadLocalContextStore() {}

  @Override
  public void addContext(PrefabContext prefabContext) {
    PREFAB_CONTEXT_SET_THREAD_LOCAL.get().addContext(prefabContext);
  }

  @Override
  public Optional<PrefabContextSetReadable> setContext(
    PrefabContextSetReadable prefabContextSetReadable
  ) {
    PrefabContextSet previousContext = PREFAB_CONTEXT_SET_THREAD_LOCAL.get();
    if (prefabContextSetReadable instanceof PrefabContextSet) {
      PREFAB_CONTEXT_SET_THREAD_LOCAL.set((PrefabContextSet) prefabContextSetReadable);
    } else {
      PrefabContextSet prefabContextSet = new PrefabContextSet();
      for (PrefabContext context : prefabContextSetReadable.getContexts()) {
        prefabContextSet.addContext(context);
      }
      PREFAB_CONTEXT_SET_THREAD_LOCAL.set(prefabContextSet);
    }

    return Optional.ofNullable(previousContext);
  }

  @Override
  public Optional<PrefabContextSetReadable> clearContext() {
    PrefabContextSetReadable previousContext = PREFAB_CONTEXT_SET_THREAD_LOCAL.get();
    PREFAB_CONTEXT_SET_THREAD_LOCAL.remove();
    return Optional.of(previousContext);
  }

  @Override
  public Optional<PrefabContextSetReadable> getContext() {
    return Optional.ofNullable(PREFAB_CONTEXT_SET_THREAD_LOCAL.get());
  }
}
