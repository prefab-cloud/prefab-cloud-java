package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import javax.annotation.Nullable;

public class FeatureFlagClientImpl extends AbstractFeatureFlagResolverImpl {

  private final ConfigClient configClient;

  public FeatureFlagClientImpl(ConfigClient configClient) {
    this.configClient = configClient;
  }

  protected Optional<Prefab.ConfigValue> getConfigValue(
    String feature,
    @Nullable PrefabContextSetReadable prefabContext
  ) {
    return configClient.get(feature, prefabContext);
  }
}
