package cloud.prefab.client.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.slf4j.Marker;

public abstract class BaseTurboFilter extends TurboFilter {

  protected final ConfigClient configClient;
  private final ThreadLocal<Boolean> recursionCheck = ThreadLocal.withInitial(() -> false
  );

  BaseTurboFilter(ConfigClient configClient) {
    this.configClient = configClient;
  }

  abstract Optional<Prefab.LogLevel> getLogLevel(Logger logger, Level level);

  @Override
  public FilterReply decide(
    Marker marker,
    Logger logger,
    Level level,
    String s,
    Object[] objects,
    Throwable throwable
  ) {
    if (!configClient.isReady()) {
      return FilterReply.NEUTRAL;
    }
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
      Optional<Prefab.LogLevel> loglevelMaybe = getLogLevel(logger, level);
      if (loglevelMaybe.isPresent()) {
        Level calculatedMinLogLevelToAccept = LogbackLevelMapper.LEVEL_MAP.get(
          loglevelMaybe.get()
        );
        if (level.isGreaterOrEqual(calculatedMinLogLevelToAccept)) {
          return FilterReply.ACCEPT;
        }
        return FilterReply.DENY;
      }
      return FilterReply.NEUTRAL;
    } finally {
      recursionCheck.set(false);
    }
  }
}
