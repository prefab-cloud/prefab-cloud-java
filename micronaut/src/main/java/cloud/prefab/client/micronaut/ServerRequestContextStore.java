package cloud.prefab.client.micronaut;

import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import io.micronaut.http.context.ServerRequestContext;
import java.util.Optional;

/**
 * This supersedes the standard ThreadLocalContext store for micronaut because
 * micronaut is an event-based server, so a request can be handled by many threads.
 * Instead of using a ThreadLocal, we stash the context into the attributes of the current
 * HttpRequest via ServerRequestContext.currentRequest()
 */
public class ServerRequestContextStore implements ContextStore {

  public static final String ATTRIBUTE_NAME = "prefab-contexts";

  @Override
  public void addContext(PrefabContext prefabContext) {
    getPrefabContextSet()
      .ifPresentOrElse(
        prefabContextSet -> prefabContextSet.addContext(prefabContext),
        () -> setContext(prefabContext)
      );
  }

  @Override
  public Optional<PrefabContextSetReadable> setContext(
    PrefabContextSetReadable prefabContextSetReadable
  ) {
    return ServerRequestContext
      .currentRequest()
      .map(req -> {
        Optional<PrefabContextSetReadable> currentContext = getContext();
        req.setAttribute(
          ATTRIBUTE_NAME,
          PrefabContextSet.convert(prefabContextSetReadable)
        );
        return currentContext;
      })
      .orElse(Optional.empty());
  }

  @Override
  public Optional<PrefabContextSetReadable> clearContext() {
    Optional<PrefabContextSetReadable> currentContext = getContext();
    ServerRequestContext
      .currentRequest()
      .ifPresent(objectHttpRequest -> objectHttpRequest.setAttribute(ATTRIBUTE_NAME, null)
      );
    return currentContext;
  }

  @Override
  public Optional<PrefabContextSetReadable> getContext() {
    return getPrefabContextSet().map(PrefabContextSetReadable::readOnlyContextSetView);
  }

  private Optional<PrefabContextSet> getPrefabContextSet() {
    return ServerRequestContext
      .currentRequest()
      .flatMap(objectHttpRequest ->
        objectHttpRequest.getAttribute(ATTRIBUTE_NAME, PrefabContextSet.class)
      );
  }
}
