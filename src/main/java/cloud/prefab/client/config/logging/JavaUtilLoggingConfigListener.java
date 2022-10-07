package cloud.prefab.client.config.logging;

import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.domain.Prefab.LogLevel;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Log levels require some translation:
 * - FATAL -> SEVERE
 * - ERROR -> SEVERE
 * - WARN -> WARNING
 * - DEBUG -> FINE
 * - TRACE -> FINER
 * <p>
 * OFF, CONFIG, FINEST, and ALL are unsupported
 */
public class JavaUtilLoggingConfigListener extends AbstractLoggingListener<Level> {

  private static final ConfigChangeListener INSTANCE = new JavaUtilLoggingConfigListener();

  private static final String ROOT_LOGGER = "";

  private static final Map<LogLevel, Level> LEVEL_MAP = ImmutableMap
    .<LogLevel, Level>builder()
    .put(LogLevel.FATAL, Level.SEVERE)
    .put(LogLevel.ERROR, Level.SEVERE)
    .put(LogLevel.WARN, Level.WARNING)
    .put(LogLevel.INFO, Level.INFO)
    .put(LogLevel.DEBUG, Level.FINE)
    .put(LogLevel.TRACE, Level.FINER)
    .build();

  public static ConfigChangeListener getInstance() {
    return INSTANCE;
  }

  private JavaUtilLoggingConfigListener() {}

  @Override
  protected Map<LogLevel, Level> getValidLevels() {
    return LEVEL_MAP;
  }

  @Override
  protected void setDefaultLevel(Optional<Level> level) {
    setLevel(ROOT_LOGGER, level);
  }

  @Override
  protected void setLevel(String loggerName, Optional<Level> level) {
    Logger logger = Logger.getLogger(loggerName);

    logger.setLevel(level.orElse(null));
  }
}
