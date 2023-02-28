package cloud.prefab.client.value;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveLong extends AbstractLiveValue<Long> {

  public LiveLong(ConfigClient configClient, String key) {
    super(configClient, key);
  }

  @Override
  public Optional<Long> resolve(Prefab.ConfigValue value) {
    if (value.hasInt()) {
      return Optional.of(value.getInt());
    } else {
      return Optional.empty();
    }
  }
}
