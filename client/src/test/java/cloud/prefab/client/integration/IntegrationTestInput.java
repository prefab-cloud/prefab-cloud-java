package cloud.prefab.client.integration;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.value.LiveObject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Optional;

public class IntegrationTestInput {

  private final String key;
  private final Optional<String> flag;
  private final String lookupKey;
  private final Map<String, Map<String, Object>> context;
  private final Optional<Object> defaultValue;

  @JsonCreator
  public IntegrationTestInput(
    @JsonProperty("key") String key,
    @JsonProperty("flag") Optional<String> flag,
    @JsonProperty("lookup_key") String lookupKey,
    @JsonProperty("context") Map<String, Map<String, Object>> context,
    @JsonProperty("default") Optional<Object> defaultValue
  ) {
    this.key = key;
    this.flag = flag;
    this.lookupKey = lookupKey;
    this.context = context;
    this.defaultValue = defaultValue;
  }

  public Object getWithFallback(PrefabCloudClient client) {
    LiveObject liveObject = new LiveObject(client.configClient(), getKey());
    return liveObject.orElse(defaultValue.orElse(null));
  }

  public Object getWithoutFallback(PrefabCloudClient client) {
    if (defaultValue.isPresent()) {
      return getWithFallback(client);
    } else {
      LiveObject liveObject = new LiveObject(client.configClient(), getKey());
      return liveObject.get();
    }
  }

  public boolean featureIsOnFor(PrefabCloudClient client) {
    return client
      .featureFlagClient()
      .featureIsOn(getFlag().get(), PrefabContextFactory.from(getContext()));
  }

  public long getFeatureFor(PrefabCloudClient client) {
    return client
      .featureFlagClient()
      .get(getFlag().get(), PrefabContextFactory.from(getContext()))
      .get()
      .getInt();
  }

  public String getKey() {
    return key;
  }

  public Optional<String> getFlag() {
    return flag;
  }

  public Map<String, Map<String, Object>> getContext() {
    return context;
  }

  public Optional<Object> getDefault() {
    return defaultValue;
  }
}
