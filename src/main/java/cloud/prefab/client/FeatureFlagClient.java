package cloud.prefab.client;

import cloud.prefab.client.config.ConfigLoader;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.client.util.RandomProvider;
import cloud.prefab.client.util.RandomProviderIF;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.protobuf.ProtocolStringList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.core.jackson.ListOfMapEntryDeserializer;

public class FeatureFlagClient {

  private final ConfigStore configStore;

  private RandomProviderIF randomProvider = new RandomProvider();

  public FeatureFlagClient(ConfigStore configStore) {
    this.configStore = configStore;
  }

  /**
   * Simplified method for boolean flags. Returns false if flag is not defined.
   *
   * @param feature
   * @return
   */
  public boolean featureIsOn(String feature) {
    return featureIsOnFor(feature, Optional.empty(), ImmutableMap.of());
  }

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
  public boolean featureIsOnFor(
    String feature,
    String lookupKey,
    Map<String, Object> attributes
  ) {
    return featureIsOnFor(feature, Optional.of(lookupKey), attributes);
  }

  public boolean featureIsOnFor(
    String feature,
    Optional<String> lookupKey,
    Map<String, Object> attributes
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
  public Optional<Prefab.ConfigValue> get(
    String feature,
    Optional<String> lookupKey,
    Map<String, Object> properties
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
    return configStore.get(feature, attributes);
  }

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

  public FeatureFlagClient setRandomProvider(RandomProviderIF randomProvider) {
    this.randomProvider = randomProvider;
    return this;
  }
}
