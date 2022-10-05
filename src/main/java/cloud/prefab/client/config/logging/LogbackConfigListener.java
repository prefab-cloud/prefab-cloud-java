package cloud.prefab.client.config.logging;

import ch.qos.logback.classic.Level;
import cloud.prefab.client.config.ConfigChangeListener;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogbackConfigListener extends AbstractLoggingListener<Level> {

  private static final ConfigChangeListener INSTANCE = new LogbackConfigListener();

  private static final Map<String, Level> LEVEL_MAP = ImmutableMap
    .<String, Level>builder()
    .put("OFF", Level.OFF)
    .put("ERROR", Level.ERROR)
    .put("WARN", Level.WARN)
    .put("INFO", Level.INFO)
    .put("DEBUG", Level.DEBUG)
    .put("TRACE", Level.TRACE)
    .put("ALL", Level.ALL)
    .build();

  public static ConfigChangeListener getInstance() {
    return INSTANCE;
  }

  private LogbackConfigListener() {}

  @Override
  protected Map<String, Level> getValidLevels() {
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
