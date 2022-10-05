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
  implements ConfigChangeListener {

  private static final String LOG_LEVEL_PREFIX = "log-level.";
  private static final String DEFAULT_LOG_LEVEL = LOG_LEVEL_PREFIX + "default";

  protected final Logger LOG = LoggerFactory.getLogger(getClass());

  protected abstract Map<LogLevel, LEVEL_TYPE> getValidLevels();

  protected abstract void setDefaultLevel(Optional<LEVEL_TYPE> level);

  protected abstract void setLevel(String loggerName, Optional<LEVEL_TYPE> level);

  @Override
  public final void onChange(ConfigChangeEvent changeEvent) {
    if (isLogLevelChange(changeEvent)) {
      Optional<LEVEL_TYPE> level = changeEvent
        .getNewValue()
        .filter(this::isLogLevel)
        .map(newValue -> getValidLevels().get(newValue.getLogLevel()));

      String key = changeEvent.getKey();
      if (key.equals(DEFAULT_LOG_LEVEL)) {
        setDefaultLevel(level);
      } else if (key.startsWith(LOG_LEVEL_PREFIX)) {
        String loggerName = key.substring(LOG_LEVEL_PREFIX.length());

        setLevel(loggerName, level);
      } else {
        LOG.warn(
          "Expected log level override to start with '{}', but was '{}'",
          LOG_LEVEL_PREFIX,
          key
        );
      }
    }
  }

  private boolean isLogLevelChange(ConfigChangeEvent changeEvent) {
    boolean newValueIsLogLevel = changeEvent
      .getNewValue()
      .map(this::isLogLevel)
      .orElse(false);
    boolean oldValueIsLogLevel = changeEvent
      .getOldValue()
      .map(this::isLogLevel)
      .orElse(false);

    return newValueIsLogLevel || oldValueIsLogLevel;
  }

  private boolean isLogLevel(ConfigValue value) {
    return TypeCase.LOG_LEVEL.equals(value.getTypeCase());
  }
}
