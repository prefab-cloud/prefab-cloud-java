package cloud.prefab.client.internal;

import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSetReadable;
import java.util.Optional;

public abstract class DelegatingContextStore implements ContextStore {

  abstract ContextStore getContextStore();

  @Override
  public void addContext(PrefabContext prefabContext) {
    getContextStore().addContext(prefabContext);
  }

  @Override
  public Optional<PrefabContextSetReadable> clearContext() {
    return getContextStore().clearContext();
  }

  @Override
  public Optional<PrefabContextSetReadable> setContext(
    PrefabContextSetReadable prefabContextSetReadable
  ) {
    return Optional.empty();
  }

  @Override
  public Optional<PrefabContextSetReadable> getContext() {
    return getContextStore().getContext();
  }
}
