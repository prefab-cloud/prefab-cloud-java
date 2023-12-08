package cloud.prefab.client.config.logging;

import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.domain.Prefab.LogLevel;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;

/**
 * OFF and ALL log levels are unsupported
 */
public class Log4j1ConfigListener extends AbstractLoggingListener<Level> {

  private static final LogLevelChangeListener INSTANCE = new Log4j1ConfigListener();

  private static final Map<LogLevel, Level> LEVEL_MAP = ImmutableMap
    .<LogLevel, Level>builder()
    .put(LogLevel.FATAL, Level.FATAL)
    .put(LogLevel.ERROR, Level.ERROR)
    .put(LogLevel.WARN, Level.WARN)
    .put(LogLevel.INFO, Level.INFO)
    .put(LogLevel.DEBUG, Level.DEBUG)
    .put(LogLevel.TRACE, Level.TRACE)
    .build();

  public static LogLevelChangeListener getInstance() {
    return INSTANCE;
  }

  private Log4j1ConfigListener() {}

  @Override
  protected Map<LogLevel, Level> getValidLevels() {
    return LEVEL_MAP;
  }

  @Override
  protected void setDefaultLevel(Optional<Level> level) {
    LogManager.getRootLogger().setLevel(level.orElse(null));
  }

  @Override
  protected void setLevel(String loggerName, Optional<Level> level) {
    LogManager.getLogger(loggerName).setLevel(level.orElse(null));
  }
}
