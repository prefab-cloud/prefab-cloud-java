package cloud.prefab.context;

import java.util.Optional;
import java.util.concurrent.Callable;

public class PrefabContextHelper {

  private static final ThreadLocal<PrefabContext> contextThreadLocal = new ThreadLocal<>();

  public static void saveContextToThreadLocal(PrefabContext prefabContext) {
    contextThreadLocal.set(prefabContext);
  }

  public static void saveContextToThreadLocal(Optional<PrefabContext> prefabContext) {
    if (prefabContext.isPresent()) {
      saveContextToThreadLocal(prefabContext.get());
    } else {
      contextThreadLocal.remove();
    }
  }

  public static void clearContextThreadLocal() {
    contextThreadLocal.remove();
  }

  public static Optional<PrefabContext> getContextFromThreadLocal() {
    return Optional.ofNullable(contextThreadLocal.get());
  }

  /**
   * Replaces the contents of the PrefabContext while the specified callable is running,
   * then restores the PrefabContext to original value
   * @param prefabContext the contents of PrefabContext while runnable is running
   * @param callable to run
   * @return the return value of the callable
   */
  public static <T> T performWorkWithContext(
    PrefabContext prefabContext,
    Callable<T> callable
  ) throws Exception {
    final Optional<PrefabContext> contextBackup = getContextFromThreadLocal();
    try {
      saveContextToThreadLocal(prefabContext);
      return callable.call();
    } finally {
      saveContextToThreadLocal(contextBackup);
    }
  }

  /**
   * Replaces the contents of the PrefabContext while the specified runnable is running,
   * then restores the PrefabContext to original value
   * @param prefabContext the contents of PrefabContext while runnable is running
   * @param runnable to run
   */
  public static void performWorkWithContext(
    PrefabContext prefabContext,
    Runnable runnable
  ) {
    final Optional<PrefabContext> contextBackup = getContextFromThreadLocal();
    try {
      saveContextToThreadLocal(prefabContext);
      runnable.run();
    } finally {
      saveContextToThreadLocal(contextBackup);
    }
  }

  /**
   * Replaces the contents of the PrefabContext ThreadLocal for until the returned,
   * then restores the PrefabContext to original value. For use in try-with-resources blocks
   * @param context the contents of PrefabContext while work in the try-with-resources block is happening
   * @return an AutoClosable PrefabContextClosable that will revert the context on close
   */
  public static PrefabContextScope performWorkWithAutoClosingContext(
    PrefabContext context
  ) {
    PrefabContextScope prefabContextScope = new PrefabContextScope(
      getContextFromThreadLocal()
    );
    contextThreadLocal.set(context);
    return prefabContextScope;
  }

  public static class PrefabContextScope implements AutoCloseable {

    private final Optional<PrefabContext> contextBackup;

    private PrefabContextScope(Optional<PrefabContext> contextBackup) {
      this.contextBackup = contextBackup;
    }

    @Override
    public void close() {
      PrefabContextHelper.saveContextToThreadLocal(contextBackup);
    }
  }
}
