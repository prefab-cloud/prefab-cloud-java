package cloud.prefab.client.value;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveDouble extends AbstractLiveValue<Double> {

  private static final Logger LOG = LoggerFactory.getLogger(LiveDouble.class);

  public LiveDouble(ConfigClient configClient, String key) {
    super(configClient, key);
  }

  @Override
  public Optional<Double> resolve(Prefab.ConfigValue value) {
    if (value.hasDouble()) {
      return Optional.of(value.getDouble());
    } else {
      LOG.warn(
        String.format(
          "Config value for key '%s' used as a double does not have a double value set, will treat as empty",
          key
        )
      );
      return Optional.empty();
    }
  }
}
