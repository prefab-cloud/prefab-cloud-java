package cloud.prefab.client.value;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.domain.Prefab;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveDuration extends AbstractLiveValue<Duration> {

  private static final Logger LOG = LoggerFactory.getLogger(LiveDuration.class);

  public LiveDuration(ConfigClient configClient, String key) {
    super(configClient, key);
  }

  @Override
  public Optional<Duration> resolve(Prefab.ConfigValue value) {
    if (value.hasDuration()) {
      return Optional.of(ConfigValueUtils.asDuration(value));
    } else {
      LOG.warn(
        String.format(
          "Config value for key '%s' used as a long does not have duration value, will treat as empty",
          key
        )
      );
      return Optional.empty();
    }
  }
}
