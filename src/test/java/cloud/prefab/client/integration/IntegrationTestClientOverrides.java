package cloud.prefab.client.integration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

public class IntegrationTestClientOverrides {

  private final Optional<String> namespace;
  private final Optional<Integer> onNoDefault;

  @JsonCreator
  public IntegrationTestClientOverrides(
    @JsonProperty("namespace") Optional<String> namespace,
    @JsonProperty("on_no_default") Optional<Integer> onNoDefault
  ) {
    this.namespace = namespace;
    this.onNoDefault = onNoDefault;
  }

  public static IntegrationTestClientOverrides empty() {
    return new IntegrationTestClientOverrides(Optional.empty(), Optional.empty());
  }

  public Optional<String> getNamespace() {
    return namespace;
  }

  public Optional<Integer> getOnNoDefault() {
    return onNoDefault;
  }
}
