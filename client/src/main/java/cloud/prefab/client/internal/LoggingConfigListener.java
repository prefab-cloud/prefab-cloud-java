package cloud.prefab.client.internal;

import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.domain.Prefab.ConfigValue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LoggingConfigListener implements ConfigChangeListener {

  private static final Logger LOG = LoggerFactory.getLogger(LoggingConfigListener.class);
  private final Supplier<Boolean> systemInitializedSupplier;

  LoggingConfigListener(Supplier<Boolean> systemInitializedSupplier) {
    this.systemInitializedSupplier = systemInitializedSupplier;
  }

  @Override
  public void onChange(ConfigChangeEvent changeEvent) {
    if (systemInitializedSupplier.get()) {
      if (changeEvent.getNewValue().isEmpty()) {
        LOG.info("Config value '{}' removed", changeEvent.getKey());
      } else if (changeEvent.getOldValue().isEmpty()) {
        LOG.info("Config value '{}' added", changeEvent.getKey());
      } else {
        LOG.info("Config value '{}' updated", changeEvent.getKey());
      }
    } else {
      LOG.trace(
        "Not logging config change event {} before system initialization",
        changeEvent
      );
    }
  }
}
