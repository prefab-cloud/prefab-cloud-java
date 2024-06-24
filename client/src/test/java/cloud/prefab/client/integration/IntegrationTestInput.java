package cloud.prefab.client.integration;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.value.LiveObject;
import cloud.prefab.domain.Prefab;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Optional;

public class IntegrationTestInput {

  private final String key;
  private final Optional<String> flag;
  private final String lookupKey;
  private Map<String, Map<String, Object>> context;
  private final Optional<Object> defaultValue;

  @JsonCreator
  public IntegrationTestInput(
    @JsonProperty("key") String key,
    @JsonProperty("flag") Optional<String> flag,
    @JsonProperty("lookup_key") String lookupKey,
    @JsonProperty("context") Map<String, Map<String, Object>> context,
    @JsonProperty("default") Optional<Object> defaultValue,
    @JsonProperty("client") Optional<String> client
  ) {
    this.key = key;
    this.flag = flag;
    this.lookupKey = lookupKey;
    this.context = context;
    this.defaultValue = defaultValue;
  }

  public Object getWithFallback(PrefabCloudClient client) {
    Optional<Prefab.ConfigValue> configValueOptional = client
      .configClient()
      .get(getKey(), PrefabContextFactory.from(getContext()));
    return configValueOptional
      .flatMap(ConfigValueUtils::asObject)
      .orElseGet(() -> defaultValue.orElse(null));
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

  public void setContext(Map<String, Map<String, Object>> context) {
    this.context = Map.copyOf(context);
  }
}
