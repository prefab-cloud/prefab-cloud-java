package cloud.prefab.client.value;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.domain.Prefab;
import java.util.Optional;

public class LiveObject extends AbstractLiveValue<Object> {

  public LiveObject(ConfigClient configClient, String key) {
    super(configClient, key);
  }

  @Override
  public Optional<Object> resolve(Prefab.ConfigValue value) {
    return ConfigValueUtils.asObject(value);
  }
}
