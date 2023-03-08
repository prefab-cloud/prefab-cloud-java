package cloud.prefab.client.config.logging;

import ch.qos.logback.classic.Level;
import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.domain.Prefab.LogLevel;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log levels require some translation:
 * - FATAL -> ERROR
 * <p>
 * OFF and ALL are unsupported
 */
public class LogbackConfigListener extends AbstractLoggingListener<Level> {

  private static final ConfigChangeListener INSTANCE = new LogbackConfigListener();

  public static ConfigChangeListener getInstance() {
    return INSTANCE;
  }

  private LogbackConfigListener() {}

  @Override
  protected Map<LogLevel, Level> getValidLevels() {
    return LogbackLevelMapper.LEVEL_MAP;
  }

  @Override
  protected void setDefaultLevel(Optional<Level> level) {
    setLevel(Logger.ROOT_LOGGER_NAME, level);
  }

  @Override
  protected void setLevel(String loggerName, Optional<Level> level) {
    Logger logger = LoggerFactory.getLogger(loggerName);

    if (logger instanceof ch.qos.logback.classic.Logger) {
      ((ch.qos.logback.classic.Logger) logger).setLevel(level.orElse(null));
    } else {
      LOG.warn(
        "Unable to change log level for '{}' because logging implementation is not Logback: '{}'",
        loggerName,
        logger.getClass()
      );
    }
  }
}
