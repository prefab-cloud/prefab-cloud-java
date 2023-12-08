package cloud.prefab.client.config.logging;

import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.domain.Prefab.LogLevel;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * OFF and ALL log levels are unsupported
 */
public class Log4j2ConfigListener extends AbstractLoggingListener<Level> {

  private static final LogLevelChangeListener INSTANCE = new Log4j2ConfigListener();

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

  private Log4j2ConfigListener() {}

  @Override
  protected Map<LogLevel, Level> getValidLevels() {
    return LEVEL_MAP;
  }

  @Override
  protected void setDefaultLevel(Optional<Level> level) {
    Configurator.setRootLevel(level.orElse(null));
  }

  @Override
  protected void setLevel(String loggerName, Optional<Level> level) {
    Configurator.setLevel(loggerName, level.orElse(null));
  }
}
