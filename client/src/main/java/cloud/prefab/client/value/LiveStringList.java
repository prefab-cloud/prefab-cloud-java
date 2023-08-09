package cloud.prefab.client.value;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveStringList extends AbstractLiveValue<List<String>> {

  private static final Logger LOG = LoggerFactory.getLogger(LiveStringList.class);

  public LiveStringList(ConfigClient configClient, String key) {
    super(configClient, key);
  }

  @Override
  public Optional<List<String>> resolve(Prefab.ConfigValue value) {
    if (value.hasStringList()) {
      return Optional.of(value.getStringList().getValuesList());
    } else {
      LOG.warn(
        String.format(
          "Config value for key '%s' used as a stringlist does not have a string value, will treat as empty",
          key
        )
      );
      return Optional.empty();
    }
  }
}
