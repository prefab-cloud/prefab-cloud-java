package cloud.prefab.client.integration;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextHelper;
import cloud.prefab.context.PrefabContextSetReadable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.MustBeClosed;
import org.junit.jupiter.api.function.Executable;

public class IntegrationTestCaseDescriptor {

  private final String name;
  private final String client;
  private final IntegrationTestFunction function;
  private final IntegrationTestClientOverrides clientOverrides;
  private final IntegrationTestInput input;
  private final IntegrationTestExpectation expected;

  @JsonCreator
  public IntegrationTestCaseDescriptor(
    @JsonProperty("name") String name,
    @JsonProperty("client") String client,
    @JsonProperty("function") IntegrationTestFunction function,
    @JsonProperty("client_overrides") IntegrationTestClientOverrides clientOverrides,
    @JsonProperty("input") IntegrationTestInput input,
    @JsonProperty("expected") IntegrationTestExpectation expected
  ) {
    this.name = name;
    this.client = client;
    this.function = function;
    this.clientOverrides =
      MoreObjects.firstNonNull(clientOverrides, IntegrationTestClientOverrides.empty());
    this.input = input;
    this.expected = expected;
  }

  public Executable asExecutable(PrefabContextSetReadable prefabContext) {
    return () -> {
      try (PrefabCloudClient client = buildClient(clientOverrides)) {
        PrefabContextHelper helper = new PrefabContextHelper(client.configClient());
        try (
          PrefabContextHelper.PrefabContextScope ignored = helper.performWorkWithAutoClosingContext(
            prefabContext
          )
        ) {
          getExpected().verifyScenario(client, getFunction(), getInput());
        }
      }
    };
  }

  public String getName() {
    return name;
  }

  public String getClient() {
    return client;
  }

  public IntegrationTestFunction getFunction() {
    return function;
  }

  public IntegrationTestClientOverrides getClientOverrides() {
    return clientOverrides;
  }

  public IntegrationTestInput getInput() {
    return input;
  }

  public IntegrationTestExpectation getExpected() {
    return expected;
  }

  @MustBeClosed
  private PrefabCloudClient buildClient(IntegrationTestClientOverrides clientOverrides) {
    String apiKey = System.getenv("PREFAB_INTEGRATION_TEST_API_KEY");
    if (apiKey == null) {
      throw new IllegalStateException(
        "Env var PREFAB_INTEGRATION_TEST_API_KEY is not set"
      );
    }

    Options options = new Options()
      .setApikey(apiKey)
      .setPrefabApiUrl("https://api.staging-prefab.cloud")
      .setInitializationTimeoutSec(1000);

    clientOverrides.getNamespace().ifPresent(options::setNamespace);
    clientOverrides.getInitTimeoutSeconds().ifPresent(options::setInitializationTimeoutSec);


    return new PrefabCloudClient(options);
  }
}
