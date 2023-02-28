package cloud.prefab.client.value;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;

public class LiveString extends AbstractLiveValue<String> {

  public LiveString(ConfigClient configClient, String key) {
    super(configClient, key);
  }

  @Override
  public Optional<String> resolve(Prefab.ConfigValue value) {
    if (value.hasString()) {
      return Optional.of(value.getString());
    } else {
      return Optional.empty();
    }
  }
}
