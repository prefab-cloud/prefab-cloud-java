package cloud.prefab.client.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import cloud.prefab.client.ConfigClient;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

public class LogbackTurboFilter extends TurboFilter {

  private final ThreadLocal<Boolean> recursionCheck = ThreadLocal.withInitial(() -> false
  );

  private final ConfigClient configClient;

  private LogbackTurboFilter(ConfigClient configClient) {
    this.configClient = configClient;
  }

  public static void install(ConfigClient configClient) {
    LogbackTurboFilter filter = new LogbackTurboFilter(configClient);
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.addTurboFilter(filter);
  }

  @Override
  public FilterReply decide(
    Marker marker,
    Logger logger,
    Level level,
    String s,
    Object[] objects,
    Throwable throwable
  ) {
    if (recursionCheck.get()) {
      return FilterReply.NEUTRAL;
    } else {
      recursionCheck.set(true);
    }
    configClient.reportLoggerUsage(
      logger.getName(),
      LogbackLevelMapper.REVERSE_LEVEL_MAP.get(level),
      1
    );

    try {
      return FilterReply.NEUTRAL;
    } finally {
      recursionCheck.set(false);
    }
  }
}
