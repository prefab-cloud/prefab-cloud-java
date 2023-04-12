package cloud.prefab.context;

import java.util.Optional;

public class PrefabContextHelper {

  private static final ThreadLocal<Context> contextThreadLocal = new ThreadLocal<>();

  public static void saveContextToThreadLocal(Context context) {
    contextThreadLocal.set(context);
  }

  public static void clearContextThreadLocal() {
    contextThreadLocal.remove();
  }

  public static Optional<Context> getContextFromThreadLocal() {
    return Optional.ofNullable(contextThreadLocal.get());
  }
}
