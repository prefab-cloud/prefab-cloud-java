package cloud.prefab.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingConfigListener implements ConfigChangeListener {

  private static final Logger LOG = LoggerFactory.getLogger(LoggingConfigListener.class);
  private static final ConfigChangeListener INSTANCE = new LoggingConfigListener();

  public static ConfigChangeListener getInstance() {
    return INSTANCE;
  }

  private LoggingConfigListener() {}

  @Override
  public void onChange(ConfigChangeEvent changeEvent) {
    if (changeEvent.getNewValue().isEmpty()) {
      LOG.info(
        "Config value '{}' removed. Previous value was '{}'",
        changeEvent.getKey(),
        changeEvent.getOldValue().get()
      );
    } else if (changeEvent.getOldValue().isEmpty()) {
      LOG.info(
        "Config value '{}' added. New value is '{}'",
        changeEvent.getKey(),
        changeEvent.getNewValue().get()
      );
    } else {
      LOG.info(
        "Config value '{}' updated. Previous value was '{}', new value is '{}'",
        changeEvent.getKey(),
        changeEvent.getOldValue().get(),
        changeEvent.getNewValue().get()
      );
    }
  }
}
