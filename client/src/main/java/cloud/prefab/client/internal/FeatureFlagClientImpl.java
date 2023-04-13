package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.domain.Prefab;
import java.util.Map;
import java.util.Optional;

public class FeatureFlagClientImpl extends AbstractFeatureFlagResolverImpl {

  private final ConfigClient configClient;

  public FeatureFlagClientImpl(ConfigClient configClient) {
    this.configClient = configClient;
  }

  protected Optional<Prefab.ConfigValue> getConfigValue(
    String feature,
    Optional<PrefabContext> prefabContextOptional
  ) {
    return configClient.get(feature, prefabContextOptional);
  }
}
