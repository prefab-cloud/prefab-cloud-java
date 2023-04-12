package cloud.prefab.context;

import java.util.Optional;

public class PrefabContextHelper {

  private static final ThreadLocal<PrefabContext> contextThreadLocal = new ThreadLocal<>();

  public static void saveContextToThreadLocal(PrefabContext prefabContext) {
    contextThreadLocal.set(prefabContext);
  }

  public static void clearContextThreadLocal() {
    contextThreadLocal.remove();
  }

  public static Optional<PrefabContext> getContextFromThreadLocal() {
    return Optional.ofNullable(contextThreadLocal.get());
  }
}
