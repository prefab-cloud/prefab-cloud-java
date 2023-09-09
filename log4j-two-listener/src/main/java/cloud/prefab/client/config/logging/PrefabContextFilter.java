package cloud.prefab.client.config.logging;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

public class PrefabContextFilter extends AbstractFilter {

  private final ThreadLocal<Boolean> recursionCheck = ThreadLocal.withInitial(() -> false
  );
  private final ConfigClient configClient;

  /**
   * Installs PrefabContextFilter at the root logger - call only after log4j is initialized
   * Any dynamic reconfiguration of Log4j will remove this change
   * @param configClient
   */
  public static void install(ConfigClient configClient) {
    LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    loggerContext.addFilter(new PrefabContextFilter(configClient));
    loggerContext.updateLoggers();
  }

  public PrefabContextFilter(final ConfigClient configClient) {
    this.configClient = configClient;
  }

  Result decide(final String loggerName, final Level level) {
    if (!configClient.isReady()) {
      return Result.NEUTRAL;
    }
    if (recursionCheck.get()) {
      return Result.NEUTRAL;
    } else {
      recursionCheck.set(true);
    }

    try {
      configClient.reportLoggerUsage(
        loggerName,
        Log4jLevelMapper.REVERSE_LEVEL_MAP.get(level),
        1
      );
      Optional<Prefab.LogLevel> loglevelMaybe = getLogLevel(loggerName, level);
      if (loglevelMaybe.isPresent()) {
        Level calculatedMinLogLevelToAccept = Log4jLevelMapper.LEVEL_MAP.get(
          loglevelMaybe.get()
        );
        if (level.isMoreSpecificThan(calculatedMinLogLevelToAccept)) {
          return Result.ACCEPT;
        }
        return Result.DENY;
      }
      return Result.NEUTRAL;
    } finally {
      recursionCheck.set(false);
    }
  }

  private Optional<Prefab.LogLevel> getLogLevel(String loggerName, Level level) {
    return configClient.getLogLevel(loggerName);
  }

  @Override
  public Result filter(final LogEvent event) {
    return decide(event.getLoggerName(), event.getLevel());
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final Message msg,
    final Throwable t
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final Object msg,
    final Throwable t
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object... params
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object p0
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object p0,
    final Object p1
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object p0,
    final Object p1,
    final Object p2
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object p0,
    final Object p1,
    final Object p2,
    final Object p3
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object p0,
    final Object p1,
    final Object p2,
    final Object p3,
    final Object p4
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object p0,
    final Object p1,
    final Object p2,
    final Object p3,
    final Object p4,
    final Object p5
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object p0,
    final Object p1,
    final Object p2,
    final Object p3,
    final Object p4,
    final Object p5,
    final Object p6
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object p0,
    final Object p1,
    final Object p2,
    final Object p3,
    final Object p4,
    final Object p5,
    final Object p6,
    final Object p7
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object p0,
    final Object p1,
    final Object p2,
    final Object p3,
    final Object p4,
    final Object p5,
    final Object p6,
    final Object p7,
    final Object p8
  ) {
    return decide(logger.getName(), level);
  }

  @Override
  public Result filter(
    final Logger logger,
    final Level level,
    final Marker marker,
    final String msg,
    final Object p0,
    final Object p1,
    final Object p2,
    final Object p3,
    final Object p4,
    final Object p5,
    final Object p6,
    final Object p7,
    final Object p8,
    final Object p9
  ) {
    return decide(logger.getName(), level);
  }
}
