package cloud.prefab.client.config.logging;

import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.MDC;

/**
 * Helper class to use targeted logging in projects that don't have logging MDC setup
 * these helper methods will put data into the MDC for the duration of a runnable/callable
 * then restore the original MDC data
 */
public class MDCTargetedLoggingHelper {

  /**
   * Replaces the contents of the MDC context map while the specified runnable is running,
   * then restores the MDC to original value
   * @param context the contents of MDC while runnable is running
   * @param runnable to run
   */
  public static void logWithExclusiveContext(
    Map<String, String> context,
    Runnable runnable
  ) {
    final Map<String, String> contextBackup = MDC.getCopyOfContextMap();
    try {
      MDC.setContextMap(context);
      runnable.run();
    } finally {
      resetMDC(contextBackup);
    }
  }

  /**
   * Replaces the contents of the MDC context map while the specified runnable is running,
   * then restores the MDC to original value
   * @param context the contents of MDC while callable is running
   * @param callable to call
   * @return the return value of the callable
   */
  public static <T> T logWithExclusiveContext(
    Map<String, String> context,
    Callable<T> callable
  ) throws Exception {
    final Map<String, String> contextBackup = MDC.getCopyOfContextMap();
    try {
      MDC.setContextMap(context);
      return callable.call();
    } finally {
      resetMDC(contextBackup);
    }
  }

  /**
   * Replaces the contents of the MDC context map until the context closes,
   * then restores the MDC to original value. For use in try-with-resources blocks
   * @param context the contents of MDC while callable is running
   * @return an AutoClosable context object that will clean up the MDC on close
   */
  public static TargetedLoggingContext logWithExclusiveContext(
    Map<String, String> context
  ) {
    TargetedLoggingContext targetedLoggingContext = new TargetedLoggingContext(
      MDC.getCopyOfContextMap()
    );
    MDC.setContextMap(context);
    return targetedLoggingContext;
  }

  /**
   * Merges the provided context map into the MDC context map while the specified runnable is running,
   * then restores MDC to original value
   * @param context the contents of MDC while runnable is running
   * @param runnable to run
   */
  public static void logWithMergedContext(
    Map<String, String> context,
    Runnable runnable
  ) {
    final Map<String, String> contextBackup = MDC.getCopyOfContextMap();
    try {
      mergeIntoMdc(context);
      runnable.run();
    } finally {
      resetMDC(contextBackup);
    }
  }

  /**
   * Merges the provided context map into the MDC context map while the specified callable is running,
   * then restores MDC to original value
   * @param context the contents of MDC while callable is running
   * @param callable to call
   * @return the return value of the callable
   */
  public static <T> T logWithMergedContext(
    Map<String, String> context,
    Callable<T> callable
  ) throws Exception {
    final Map<String, String> contextBackup = MDC.getCopyOfContextMap();
    try {
      mergeIntoMdc(context);
      return callable.call();
    } finally {
      resetMDC(contextBackup);
    }
  }

  /**
   * Merges the provided context map into the MDC context map until the context closes,
   * then restores the MDC to original value. For use in try-with-resources blocks
   * @param context the contents of MDC while callable is running
   * @return an AutoClosable context object that will clean up the MDC on close
   */
  public static TargetedLoggingContext logWithMergedContext(Map<String, String> context) {
    TargetedLoggingContext targetedLoggingContext = new TargetedLoggingContext(
      MDC.getCopyOfContextMap()
    );
    mergeIntoMdc(context);
    return targetedLoggingContext;
  }

  public static class TargetedLoggingContext implements AutoCloseable {

    private final Map<String, String> contextBackup;

    TargetedLoggingContext(Map<String, String> contextBackup) {
      this.contextBackup = contextBackup;
    }

    @Override
    public void close() {
      resetMDC(contextBackup);
    }
  }

  private static void mergeIntoMdc(Map<String, String> context) {
    for (Map.Entry<String, String> entry : context.entrySet()) {
      MDC.put(entry.getKey(), entry.getValue());
    }
  }

  private static void resetMDC(Map<String, String> context) {
    if (context != null) {
      MDC.setContextMap(context);
    } else {
      MDC.clear();
    }
  }
}
