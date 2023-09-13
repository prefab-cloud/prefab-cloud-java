package cloud.prefab.client.integration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public class IntegrationTestClientOverrides {

  private final Optional<String> namespace;
  private final Optional<Integer> onNoDefault;
  private final Optional<Integer> initTimeoutSeconds;

  @JsonCreator
  public IntegrationTestClientOverrides(
    @JsonProperty("namespace") Optional<String> namespace,
    @JsonProperty("on_no_default") Optional<Integer> onNoDefault,
    @JsonProperty("initialization_timeout_sec") Optional<Integer> initTimeoutSeconds
  ) {
    this.namespace = namespace;
    this.onNoDefault = onNoDefault;
    this.initTimeoutSeconds = initTimeoutSeconds;
  }

  public static IntegrationTestClientOverrides empty() {
    return new IntegrationTestClientOverrides(
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
}
