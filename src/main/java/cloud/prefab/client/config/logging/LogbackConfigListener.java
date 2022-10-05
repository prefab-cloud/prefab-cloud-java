package cloud.prefab.client.config.logging;

import ch.qos.logback.classic.Level;
import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.domain.Prefab.LogLevel;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogbackConfigListener extends AbstractLoggingListener<Level> {

  private static final ConfigChangeListener INSTANCE = new LogbackConfigListener();

  // missing OFF and ALL
  private static final Map<LogLevel, Level> LEVEL_MAP = ImmutableMap
    .<LogLevel, Level>builder()
    .put(LogLevel.FATAL, Level.ERROR)
    .put(LogLevel.ERROR, Level.ERROR)
    .put(LogLevel.WARN, Level.WARN)
    .put(LogLevel.INFO, Level.INFO)
    .put(LogLevel.DEBUG, Level.DEBUG)
    .put(LogLevel.TRACE, Level.TRACE)
    .build();

  public static ConfigChangeListener getInstance() {
    return INSTANCE;
  }

  private LogbackConfigListener() {}

  @Override
  protected Map<LogLevel, Level> getValidLevels() {
    return LEVEL_MAP;
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
