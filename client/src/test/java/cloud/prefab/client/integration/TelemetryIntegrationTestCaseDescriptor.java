package cloud.prefab.client.integration;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.context.PrefabContextSetReadable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.function.Executable;

public class TelemetryIntegrationTestCaseDescriptor
  extends BaseIntegrationTestCaseDescriptor
  implements IntegrationTestCaseDescriptorIF {

  /**
   * TODO: think about whether this needs to be subclass per aggregator?
   */
  @JsonCreator
  public TelemetryIntegrationTestCaseDescriptor(
    @JsonProperty("name") String name,
    @JsonProperty("client") String client,
    @JsonProperty("function") IntegrationTestFunction function,
    @JsonProperty("aggregator") String aggregator,
    @JsonProperty("data") JsonNode data,
    @JsonProperty("expected_data") JsonNode expectedData
  ) {
    super(name, IntegrationTestClientOverrides.empty());
  }

  @Override
  protected void performVerification(PrefabCloudClient prefabCloudClient) {}
}
