package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.hubspot.liveconfig.value.LiveValue;

public abstract class PrefabLiveValue<T> extends LiveValue {

  private final ConfigResolver configResolver;
  private final String key;
  private final Function<Prefab.ConfigValue, T> transform;

  public PrefabLiveValue(
    String key,
    ConfigResolver configResolver,
    Function<Prefab.ConfigValue, T> transform
  ) {
    super(null, key, null);
    this.configResolver = configResolver;
    this.key = key;
    this.transform = transform;
  }

  @Override
  public Optional<T> getMaybe() {
    final java.util.Optional<Prefab.ConfigValue> configValue = configResolver.getConfigValue(
      key
    );
    if (configValue.isPresent()) {
      return Optional.of(transform.apply(configValue.get()));
    } else {
      return Optional.absent();
    }
  }
}
