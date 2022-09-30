package cloud.prefab.client.value;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveBoolean extends AbstractLiveValue<Boolean> {

  public LiveBoolean(ConfigClient configClient, String key) {
    super(configClient, key);
  }

  @Override
  public Optional<Boolean> resolve(Prefab.ConfigValue value) {
    if (value.hasBool()) {
      return Optional.of(value.getBool());
    } else {
      return Optional.empty();
    }
  }
}
