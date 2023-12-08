package cloud.prefab.client.config.logging;

import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.domain.Prefab.ConfigValue;
import cloud.prefab.domain.Prefab.ConfigValue.TypeCase;
import cloud.prefab.domain.Prefab.LogLevel;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLoggingListener<LEVEL_TYPE>
  implements LogLevelChangeListener {

  public static final String LOG_LEVEL_PREFIX = "log-level";

  protected final Logger LOG = LoggerFactory.getLogger(getClass());

  protected abstract Map<LogLevel, LEVEL_TYPE> getValidLevels();

  protected abstract void setDefaultLevel(Optional<LEVEL_TYPE> level);

  protected abstract void setLevel(String loggerName, Optional<LEVEL_TYPE> level);

  public static boolean keyIsLogLevel(String key) {
    return key.startsWith(LOG_LEVEL_PREFIX);
  }

  @Override
  public final void onChange(LogLevelChangeEvent changeEvent) {
    Optional<LEVEL_TYPE> level = changeEvent
      .getNewLevel()
      .map(newValue -> getValidLevels().get(newValue));
    String key = changeEvent.getLoggerName();
    if (key.equals(LOG_LEVEL_PREFIX)) {
      setDefaultLevel(level);
      LOG.info("Set default log level to '{}'", level.orElse(null));
    } else if (keyIsLogLevel(key)) {
      String loggerName = key.substring(LOG_LEVEL_PREFIX.length() + 1);
      setLevel(loggerName, level);
      LOG.info("Set log level for '{}' to '{}'", loggerName, level.orElse(null));
    } else {
      LOG.warn(
        "Expected log level override to start with '{}', but was '{}'",
        LOG_LEVEL_PREFIX,
        key
      );
    }
  }
}
