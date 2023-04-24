package cloud.prefab.context;

import cloud.prefab.client.ConfigClient;
import java.util.Optional;
import java.util.concurrent.Callable;

public class PrefabContextHelper {

  private final ContextStore contextStore;

  /**
   * Construct a context helper for a given context store
   * @param contextStore
   */
  public PrefabContextHelper(ContextStore contextStore) {
    this.contextStore = contextStore;
  }

  /**
   * Convenience constructor to create a helper for the context store attached to a client
   * @param configClient that will be queried for its contextStore at construction time
   */
  public PrefabContextHelper(ConfigClient configClient) {
    this(configClient.getContextStore());
  }

  /**
   * Replaces the contents of the PrefabContext while the specified callable is running,
   * then restores the PrefabContext to original value
   * @param prefabContext the contents of PrefabContext while runnable is running
   * @param callable to run
   * @return the return value of the callable
   */
  public <T> T performWorkWithContext(
    PrefabContextSetReadable prefabContext,
    Callable<T> callable
  ) throws Exception {
    try (PrefabContextScope ignored = performWorkWithAutoClosingContext(prefabContext)) {
      return callable.call();
    }
  }

  /**
   * Replaces the contents of the PrefabContext while the specified runnable is running,
   * then restores the PrefabContext to original value
   * @param prefabContext the contents of PrefabContext while runnable is running
   * @param runnable to run
   */
  public void performWorkWithContext(
    PrefabContextSetReadable prefabContext,
    Runnable runnable
  ) {
    try (PrefabContextScope ignored = performWorkWithAutoClosingContext(prefabContext)) {
      runnable.run();
    }
  }

  private void resetContext(Optional<PrefabContextSetReadable> oldContext) {
    if (oldContext.isPresent()) {
      contextStore.setContext(oldContext.get());
    } else {
      contextStore.clearContext();
    }
  }

  /**
   * Replaces the contents of the PrefabContext ThreadLocal for until the returned,
   * then restores the PrefabContext to original value. For use in try-with-resources blocks
   * @param context the contents of PrefabContext while work in the try-with-resources block is happening
   * @return an AutoClosable PrefabContextClosable that will revert the context on close
   */
  public PrefabContextScope performWorkWithAutoClosingContext(
    PrefabContextSetReadable context
  ) {
    return new PrefabContextScope(contextStore.setContext(context));
  }

  public class PrefabContextScope implements AutoCloseable {

    private final Optional<PrefabContextSetReadable> contextBackup;

    private PrefabContextScope(Optional<PrefabContextSetReadable> contextBackup) {
      this.contextBackup = contextBackup;
    }

    @Override
    public void close() {
      resetContext(contextBackup);
    }
  }
}
