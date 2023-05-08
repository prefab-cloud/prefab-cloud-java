package cloud.prefab.client.internal;

import cloud.prefab.client.FeatureFlagClient;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import javax.annotation.Nullable;

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
  public boolean featureIsOn(
    String feature,
    @Nullable PrefabContextSetReadable prefabContext
  ) {
    return isOn(get(feature, prefabContext));
  }

  /**
   * Return the feature flag config value for the given feature using the context loaded by Options.setConfigSupplier
   * @param feature
   * @return
   */
  @Override
  public Optional<Prefab.ConfigValue> get(String feature) {
    return getConfigValue(feature, null);
  }

  @Override
  public Optional<Prefab.ConfigValue> get(
    String feature,
    @Nullable PrefabContextSetReadable prefabContext
  ) {
    return getConfigValue(feature, prefabContext);
  }

  protected abstract Optional<Prefab.ConfigValue> getConfigValue(
    String feature,
    @Nullable PrefabContextSetReadable prefabContext
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
