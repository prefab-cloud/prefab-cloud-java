package cloud.prefab.client.internal;

import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.domain.Prefab;
import java.util.Optional;

public class ConfigStoreConfigValueDeltaCalculator
  extends AbstractConfigStoreDeltaCalculator<Prefab.ConfigValue, ConfigChangeEvent> {

  @Override
  ConfigChangeEvent createEvent(
    String name,
    Optional<Prefab.ConfigValue> oldValue,
    Optional<Prefab.ConfigValue> newValue
  ) {
    return new ConfigChangeEvent(name, oldValue, newValue);
  }
}
