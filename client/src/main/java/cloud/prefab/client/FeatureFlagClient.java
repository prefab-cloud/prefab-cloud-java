package cloud.prefab.client;

import cloud.prefab.client.util.RandomProviderIF;
import cloud.prefab.domain.Prefab;
import java.util.Map;
import java.util.Optional;

public interface FeatureFlagClient {
  boolean featureIsOn(String feature);

  boolean featureIsOnFor(String feature, String lookupKey);

  boolean featureIsOnFor(
    String feature,
    String lookupKey,
    Map<String, ? extends Object> attributes
  );

  boolean featureIsOnFor(
    String feature,
    Optional<String> lookupKey,
    Map<String, ? extends Object> attributes
  );

  Optional<Prefab.ConfigValue> get(
    String feature,
    Optional<String> lookupKey,
    Map<String, ? extends Object> properties
  );

  Optional<Prefab.ConfigValue> getFrom(
    String feature,
    Optional<String> lookupKey,
    Map<String, Prefab.ConfigValue> attributes
  );
}
