package cloud.prefab.client.config.logging;

import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.domain.Prefab;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLoggingListener<LEVEL_TYPE>
  implements ConfigChangeListener {

  private static final String LOG_LEVEL_PREFIX = "log_level.";
  private static final String DEFAULT_LOG_LEVEL = LOG_LEVEL_PREFIX + "default";

  protected final Logger LOG = LoggerFactory.getLogger(getClass());

  protected abstract Map<String, LEVEL_TYPE> getValidLevels();

  protected abstract void setDefaultLevel(Optional<LEVEL_TYPE> level);

  protected abstract void setLevel(String loggerName, Optional<LEVEL_TYPE> level);

  @Override
  public final void onChange(ConfigChangeEvent changeEvent) {
    String key = changeEvent.getKey();

    if (key.startsWith(LOG_LEVEL_PREFIX)) {
      Optional<Prefab.ConfigValue> newValue = changeEvent.getNewValue();

      if (newValue.isPresent() && !newValue.get().hasString()) {
        LOG.warn(
          "Unable to change log level for '{}' because value not a string: '{}'",
          key,
          newValue.get()
        );
      } else {
        Optional<String> levelName = newValue.map(Prefab.ConfigValue::getString);
        final Optional<LEVEL_TYPE> level;
        if (levelName.isPresent()) {
          level = toLevel(levelName.get());
          if (level.isEmpty()) {
            LOG.warn(
              "Unable to change log level for '{}' because log level is invalid: '{}'",
              key,
              levelName.get()
            );
          }
        } else {
          level = Optional.empty();
        }

        if (key.equals(DEFAULT_LOG_LEVEL)) {
          setDefaultLevel(level);
        } else {
          String loggerName = key.substring(LOG_LEVEL_PREFIX.length());

          setLevel(loggerName, level);
        }
      }
    }
  }

  private Optional<LEVEL_TYPE> toLevel(String levelName) {
    levelName = levelName.trim();

    for (Entry<String, LEVEL_TYPE> levelEntry : getValidLevels().entrySet()) {
      if (levelEntry.getKey().equalsIgnoreCase(levelName)) {
        return Optional.of(levelEntry.getValue());
      }
    }

    return Optional.empty();
  }
}
