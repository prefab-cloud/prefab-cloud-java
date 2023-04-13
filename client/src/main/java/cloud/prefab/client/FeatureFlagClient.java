package cloud.prefab.client;

import cloud.prefab.context.PrefabContext;
import cloud.prefab.domain.Prefab;
import java.util.Map;
import java.util.Optional;

public interface FeatureFlagClient {
  /**
   * Evaluate the given feature using the context loaded by Options.setConfigSupplier
   * @param feature
   * @return
   */
  boolean featureIsOn(String feature);
  /**
   * Evaluate the named feature using the provided context
   * @param feature
   * @param prefabContext the context to use for feature evaluation
   * @return
   */
  boolean featureIsOn(String feature, PrefabContext prefabContext);

  /**
   * Return the feature flag config value for the given feature using the context loaded by Options.setConfigSupplier
   * @param feature
   * @return
   */
  Optional<Prefab.ConfigValue> get(String feature);

  /**
   * Return the feature flag config value for the given feature using the context loaded by Options.setConfigSupplier
   * @param feature
   * @param prefabContext the context to use for feature evaluation
   * @return
   */
  Optional<Prefab.ConfigValue> get(String feature, PrefabContext prefabContext);
}
