package cloud.prefab.context;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Used to stack context stores when one ContextStore may not work for all cases
 * One example of this is in a micronaut code base that has both GRPC and HTTP entry points
 * Where a ContextStore backed by ServerRequestContext works for HTTP but for GRPC, ServerRequestContext is not available
 * Picks the first context store that returns true
 * NOOPs quietly if there are no valid stores
 */
public class CompositeContextStore implements ContextStore {

  private final List<ContextStore> contextStores;

  public CompositeContextStore(ContextStore... contextStores) {
    this.contextStores = Arrays.asList(contextStores);
  }

  @Override
  public void addContext(PrefabContext prefabContext) {
    getValidStore().ifPresent(cs -> cs.addContext(prefabContext));
  }

  @Override
  public Optional<PrefabContextSetReadable> setContext(
    PrefabContextSetReadable prefabContextSetReadable
  ) {
    return getValidStore().flatMap(cs -> cs.setContext(prefabContextSetReadable));
  }

  @Override
  public Optional<PrefabContextSetReadable> clearContext() {
    return getValidStore().flatMap(ContextStore::clearContext);
  }

  @Override
  public Optional<PrefabContextSetReadable> getContext() {
    return getValidStore().flatMap(ContextStore::getContext);
  }

  Optional<ContextStore> getValidStore() {
    return contextStores.stream().filter(ContextStore::isAvailable).findFirst();
  }
}
