package cloud.prefab.client;

import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import java.util.Optional;

public interface FeatureFlagClient {
  /**
   * Evaluate the given feature using the context loaded from the ContextStore
   * @param feature
   * @return
   */
  boolean featureIsOn(String feature);
  /**
   * Evaluate the named feature using the provided context comined with the context loaded from the ContextStore
   * @param feature
   * @param prefabContext the context to use for feature evaluation
   * @return
   */
  boolean featureIsOn(String feature, PrefabContextSetReadable prefabContext);

  /**
   * Return the feature flag config value for the given feature using the context loaded from the ContextStore
   * @param feature
   * @return
   */
  Optional<Prefab.ConfigValue> get(String feature);

  /**
   * Return the feature flag config value for the given feature using the provided context combined with the one loaded from the ContextStore
   * @param feature
   * @param prefabContext the context to use for feature evaluation
   * @return
   */
  Optional<Prefab.ConfigValue> get(
    String feature,
    PrefabContextSetReadable prefabContext
  );
}
