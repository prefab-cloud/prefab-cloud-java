package cloud.prefab.client.internal;

import cloud.prefab.client.FeatureFlagClient;
import cloud.prefab.client.config.ConfigLoader;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AbstractFeatureFlagResolverImpl implements FeatureFlagClient {

  @Override
  public boolean featureIsOn(String feature) {
    return featureIsOnFor(feature, Optional.empty(), ImmutableMap.of());
  }

  @Override
  public boolean featureIsOnFor(String feature, String lookupKey) {
    return isOn(get(feature, Optional.of(lookupKey), ImmutableMap.of()));
  }

  /**
   * Simplified method for boolean flags. Returns false if flag is not defined.
   *
   * @param feature
   * @param lookupKey
   * @param attributes
   * @return
   */
  @Override
  public boolean featureIsOnFor(
    String feature,
    String lookupKey,
    Map<String, ? extends Object> attributes
  ) {
    return featureIsOnFor(feature, Optional.of(lookupKey), attributes);
  }

  @Override
  public boolean featureIsOnFor(
    String feature,
    Optional<String> lookupKey,
    Map<String, ? extends Object> attributes
  ) {
    return isOn(get(feature, lookupKey, attributes));
  }

  /**
   * Fetch a feature flag and evaluate
   *
   * @param feature
   * @param lookupKey
   * @param properties
   * @return
   */
  @Override
  public Optional<Prefab.ConfigValue> get(
    String feature,
    Optional<String> lookupKey,
    Map<String, ? extends Object> properties
  ) {
    return getFrom(
      feature,
      lookupKey,
      properties
        .entrySet()
        .stream()
        .collect(
          Collectors.toMap(
            Map.Entry::getKey,
            e -> ConfigLoader.configValueFromObj(feature, e.getValue())
          )
        )
    );
  }

  @Override
  public Optional<Prefab.ConfigValue> getFrom(
    String feature,
    Optional<String> lookupKey,
    Map<String, Prefab.ConfigValue> attributes
  ) {
    if (lookupKey.isPresent()) {
      attributes.put(
        ConfigResolver.LOOKUP_KEY,
        Prefab.ConfigValue.newBuilder().setString(lookupKey.get()).build()
      );
    }
    return getConfigValue(feature, attributes);
  }

  protected abstract Optional<Prefab.ConfigValue> getConfigValue(
    String feature,
    Map<String, Prefab.ConfigValue> attributes
  );

  private boolean isOn(Optional<Prefab.ConfigValue> featureFlagVariant) {
    if (featureFlagVariant.isPresent()) {
      if (featureFlagVariant.get().hasBool()) {
        return featureFlagVariant.get().getBool();
      } else {
        // TODO log
        return false;
      }
    } else {
      return false;
    }
  }
}
