package cloud.prefab.client.internal;

import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSetReadable;
import java.util.Optional;

public class DelegatingContextStore implements ContextStore {

  private final ContextStore delegate;

  public DelegatingContextStore(ContextStore contextStore) {
    this.delegate = contextStore;
  }

  @Override
  public void addContext(PrefabContext prefabContext) {
    delegate.addContext(prefabContext);
  }

  @Override
  public void clearContexts() {
    delegate.clearContexts();
  }

  @Override
  public Optional<PrefabContextSetReadable> getContexts() {
    return delegate.getContexts();
  }
}
