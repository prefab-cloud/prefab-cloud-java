package cloud.prefab.client.integration;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.context.PrefabContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;

public class IntegrationTestInput {

  private final String key;
  private final Optional<String> flag;
  private final String lookupKey;
  private final Map<String, String> properties;
  private final Optional<String> defaultValue;

  @JsonCreator
  public IntegrationTestInput(
    @JsonProperty("key") String key,
    @JsonProperty("flag") Optional<String> flag,
    @JsonProperty("lookup_key") String lookupKey,
    @JsonProperty("properties") Map<String, String> properties,
    @JsonProperty("default") Optional<String> defaultValue
  ) {
    this.key = key;
    this.flag = flag;
    this.lookupKey = lookupKey;
    this.properties = properties;
    this.defaultValue = defaultValue;
  }

  public String getWithFallback(PrefabCloudClient client) {
    return client.configClient().liveString(getKey()).orElse(defaultValue.orElse(null));
  }

  public String getWithoutFallback(PrefabCloudClient client) {
    if (defaultValue.isPresent()) {
      return getWithFallback(client);
    } else {
      return client.configClient().liveString(getKey()).get();
    }
  }

  public boolean featureIsOnFor(PrefabCloudClient client) {
    return client
      .featureFlagClient()
      .featureIsOn(
        getFlag().get(),
        contextFrom(getResolvedLookupKey(), getResolvedProperties())
      );
  }

  private PrefabContext contextFrom(
    Optional<String> lookupKey,
    Map<String, String> resolvedProperties
  ) {
    PrefabContext.Builder prefabContextBuilder = PrefabContext
      .newBuilder("")
      .withKey(lookupKey);
    for (Map.Entry<String, String> stringStringEntry : resolvedProperties.entrySet()) {
      prefabContextBuilder.set(stringStringEntry.getKey(), stringStringEntry.getValue());
    }
    return prefabContextBuilder.build();
  }

  public long getFeatureFor(PrefabCloudClient client) {
    PrefabContext.Builder prefabContextBuilder = PrefabContext
      .newBuilder("")
      .withKey(getResolvedLookupKey());
    for (Map.Entry<String, String> stringStringEntry : getResolvedProperties()
      .entrySet()) {
      prefabContextBuilder.set(stringStringEntry.getKey(), stringStringEntry.getValue());
    }

    return client
      .featureFlagClient()
      .get(getFlag().get(), contextFrom(getResolvedLookupKey(), getResolvedProperties()))
      .get()
      .getInt();
  }

  public String getKey() {
    return key;
  }

  public Optional<String> getFlag() {
    return flag;
  }

  public String getLookupKey() {
    return lookupKey;
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public Optional<String> getDefault() {
    return defaultValue;
  }

  private Optional<String> getResolvedLookupKey() {
    if (lookupKey == null) {
      return Optional.empty();
    } else {
      return Optional.of(lookupKey);
    }
  }

  private Map<String, String> getResolvedProperties() {
    if (getProperties() == null) {
      return ImmutableMap.of();
    } else {
      return getProperties();
    }
  }
}
