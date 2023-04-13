package cloud.prefab.client.internal;

import cloud.prefab.client.FeatureFlagClient;
import cloud.prefab.client.config.ConfigLoader;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AbstractFeatureFlagResolverImpl implements FeatureFlagClient {

  /**
   * Evaluate the given feature using the context loaded by Options.setConfigSupplier
   * @param feature
   * @return
   */
  @Override
  public boolean featureIsOn(String feature) {
    return isOn(get(feature));
  }

  /**
   * Evaluate the named feature using the provided context
   * @param feature
   * @return
   */
  @Override
  public boolean featureIsOn(String feature, PrefabContext prefabContext) {
    return isOn(get(feature, prefabContext));
  }

  /**
   * Return the feature flag config value for the given feature using the context loaded by Options.setConfigSupplier
   * @param feature
   * @return
   */
  @Override
  public Optional<Prefab.ConfigValue> get(String feature) {
    return getConfigValue(feature, Optional.empty());
  }

  @Override
  public Optional<Prefab.ConfigValue> get(String feature, PrefabContext prefabContext) {
    return getConfigValue(feature, Optional.of(prefabContext));
  }

  protected abstract Optional<Prefab.ConfigValue> getConfigValue(
    String feature,
    Optional<PrefabContext> prefabContext
  );

  private boolean isOn(Optional<Prefab.ConfigValue> featureFlagVariant) {
    if (featureFlagVariant.isPresent()) {
      if (featureFlagVariant.get().hasBool()) {
        return featureFlagVariant.get().getBool();
      } else {
        return false;
      }
    } else {
      return false;
    }
  }
}
