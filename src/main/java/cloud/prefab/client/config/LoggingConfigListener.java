package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab.ConfigValue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
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
        toJson(changeEvent.getOldValue().get())
      );
    } else if (changeEvent.getOldValue().isEmpty()) {
      LOG.info(
        "Config value '{}' added. New value is '{}'",
        changeEvent.getKey(),
        toJson(changeEvent.getNewValue().get())
      );
    } else {
      LOG.info(
        "Config value '{}' updated. Previous value was '{}', new value is '{}'",
        changeEvent.getKey(),
        toJson(changeEvent.getOldValue().get()),
        toJson(changeEvent.getNewValue().get())
      );
    }
  }

  private static String toJson(ConfigValue configValue) {
    try {
      return JsonFormat.printer().omittingInsignificantWhitespace().print(configValue);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException("Error writing config value to json", e);
    }
  }
}
