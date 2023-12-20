package cloud.prefab.client.config.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

public class LogbackUtils {

  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(LogbackUtils.class);

  static void installTurboFilter(TurboFilter turboFilter) {
    ILoggerFactory iLoggerFactory = LoggerFactory.getILoggerFactory();
    if (iLoggerFactory instanceof LoggerContext) {
      LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
      loggerContext.addTurboFilter(turboFilter);
    } else {
      LOG.error(
        "unable to install {} due to LoggerContext issue, please contact us",
        turboFilter.getClass().getSimpleName()
      );
      //TODO there's a LogbackUtils in micronaut repository that handles configuration via service loader for newer slf4j/logback combos
    }
  }
}
