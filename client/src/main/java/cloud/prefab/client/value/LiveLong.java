package cloud.prefab.client.value;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveLong extends AbstractLiveValue<Long> {
  private static final Logger LOG = LoggerFactory.getLogger(LiveLong.class);

  public LiveLong(ConfigClient configClient, String key) {
    super(configClient, key);
  }

  @Override
  public Optional<Long> resolve(Prefab.ConfigValue value) {
    if (value.hasInt()) {
      return Optional.of(value.getInt());
    } else {
      LOG.warn(String.format("Config value for key '%s' used as a long does not have a integer value, will treat as empty", key));
      return Optional.empty();
    }
  }
}
