package cloud.prefab.client.value;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class LiveBoolean extends AbstractLiveValue<Boolean> {
  private static final Logger LOG = LoggerFactory.getLogger(LiveBoolean.class);

  public LiveBoolean(ConfigClient configClient, String key) {
    super(configClient, key);
  }

  @Override
  public Optional<Boolean> resolve(Prefab.ConfigValue value) {
    if (value.hasBool()) {
      return Optional.of(value.getBool());
    } else {
      LOG.warn(String.format("Config value for key '%s' used as a boolean does not have a boolean value, will treat as empty", key));
      return Optional.empty();
    }
  }
}
