package cloud.prefab.client.integration;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.context.PrefabContextHelper;
import cloud.prefab.context.PrefabContextSetReadable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.MustBeClosed;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandardIntegrationTestCaseDescriptor
  extends BaseIntegrationTestCaseDescriptor
  implements IntegrationTestCaseDescriptorIF {

  private static final Logger LOG = LoggerFactory.getLogger(
    StandardIntegrationTestCaseDescriptor.class
  );

  private final String clientName;
  private final IntegrationTestFunction function;
  private final IntegrationTestInput input;
  private final IntegrationTestExpectation expected;

  @JsonCreator
  public StandardIntegrationTestCaseDescriptor(
    @JsonProperty("name") String name,
    @JsonProperty("client") String clientName,
    @JsonProperty("function") IntegrationTestFunction function,
    @JsonProperty("client_overrides") IntegrationTestClientOverrides clientOverrides,
    @JsonProperty("input") IntegrationTestInput input,
    @JsonProperty("expected") IntegrationTestExpectation expected
  ) {
    super(
      name,
      MoreObjects.firstNonNull(clientOverrides, IntegrationTestClientOverrides.empty())
    );
    this.clientName = clientName;
    this.function = function;
    this.input = input;
    this.expected = expected;
  }

  @Override
  protected void performVerification(PrefabCloudClient prefabCloudClient) {
    getExpected().verifyScenario(prefabCloudClient, getFunction(), getInput());
  }

  public String getClient() {
    return clientName;
  }

  public IntegrationTestFunction getFunction() {
    return function;
  }

  public IntegrationTestInput getInput() {
    return input;
  }

  public IntegrationTestExpectation getExpected() {
    return expected;
  }
}
