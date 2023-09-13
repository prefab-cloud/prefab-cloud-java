package cloud.prefab.client.integration;

import cloud.prefab.client.Options;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public class IntegrationTestClientOverrides {

  private final Optional<String> namespace;
  private final Optional<Integer> onNoDefault;
  private final Optional<Integer> initTimeoutSeconds;
  private final Optional<String> prefabApiUrl;
  private final Optional<Options.OnInitializationFailure> onInitFailure;
  private final Optional<String> aggregator;
  private final Optional<Options.CollectContextMode> contextUploadMode;

  @JsonCreator
  public IntegrationTestClientOverrides(
    @JsonProperty("namespace") Optional<String> namespace,
    @JsonProperty("on_no_default") Optional<Integer> onNoDefault,
    @JsonProperty("initialization_timeout_sec") Optional<Integer> initTimeoutSeconds,
    @JsonProperty("prefab_api_url") Optional<String> prefabApiUrl,
    @JsonProperty("on_init_failure") Optional<String> onInitFailure,
    @JsonProperty("aggregator") Optional<String> aggregator,
    @JsonProperty("context_upload_mode") Optional<String> contextUploadMode
  ) {
    this.namespace = namespace;
    this.onNoDefault = onNoDefault;
    this.initTimeoutSeconds = initTimeoutSeconds;
    this.prefabApiUrl = prefabApiUrl;
    this.onInitFailure =
      onInitFailure.map(text -> {
        if (":return".equals(text)) {
          return Options.OnInitializationFailure.UNLOCK;
        }
        if (":raise".equals(text)) {
          return Options.OnInitializationFailure.RAISE;
        }
        throw new IllegalArgumentException(
          String.format("Unexpected on_init_failure property value:`%s`", text)
        );
      });
    this.aggregator = aggregator;
    this.contextUploadMode =
      contextUploadMode.map(text -> {
        if (":shape_only".equals(text)) {
          return Options.CollectContextMode.SHAPE_ONLY;
        }
        if (":none".equals(text)) {
          return Options.CollectContextMode.NONE;
        }
        if (":periodic_example".equals(text)) {
          return Options.CollectContextMode.PERIODIC_EXAMPLE;
        }
        throw new IllegalArgumentException(
          String.format("Unexpected context_upload_mode property value:`%s`", text)
        );
      });
  }

  public static IntegrationTestClientOverrides empty() {
    return new IntegrationTestClientOverrides(
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    );
  }

  public Optional<String> getNamespace() {
    return namespace;
  }

  public Optional<Integer> getOnNoDefault() {
    return onNoDefault;
  }

  public Optional<Integer> getInitTimeoutSeconds() {
    return initTimeoutSeconds;
  }

  public Optional<String> getPrefabApiUrl() {
    return prefabApiUrl;
  }

  public Optional<Options.OnInitializationFailure> getOnInitFailure() {
    return onInitFailure;
  }

  public Optional<String> getAggregator() {
    return aggregator;
  }

  public Optional<Options.CollectContextMode> getContextUploadMode() {
    return contextUploadMode;
  }
}
