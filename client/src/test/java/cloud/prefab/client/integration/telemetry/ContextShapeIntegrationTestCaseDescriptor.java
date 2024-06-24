package cloud.prefab.client.integration.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.integration.IntegrationTestClientOverrides;
import cloud.prefab.client.integration.IntegrationTestFunction;
import cloud.prefab.client.integration.PrefabContextFactory;
import cloud.prefab.client.integration.TelemetryAccumulator;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.domain.Prefab;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.MoreObjects;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextShapeIntegrationTestCaseDescriptor
  extends TelemetryIntegrationTestCaseDescriptor {

  private static final Logger LOG = LoggerFactory.getLogger(
    ContextShapeIntegrationTestCaseDescriptor.class
  );
  private final JsonNode dataNode;
  private final JsonNode expectedDataNode;

  @JsonCreator
  public ContextShapeIntegrationTestCaseDescriptor(
    @JsonProperty("name") String name,
    @JsonProperty("client") String client,
    @JsonProperty("client_overrides") IntegrationTestClientOverrides clientOverrides,
    @JsonProperty("endpoint") String endpoint,
    @JsonProperty("function") IntegrationTestFunction function,
    @JsonProperty("aggregator") String aggregator,
    @JsonProperty("data") JsonNode dataNode,
    @JsonProperty("expected_data") JsonNode expectedDataNode,
    @JsonProperty(
      "contexts"
    ) Optional<Map<String, Map<String, Map<String, Object>>>> contextMapMaybe
  ) {
    super(
      name,
      MoreObjects.firstNonNull(clientOverrides, IntegrationTestClientOverrides.empty()),
      contextMapMaybe
        .map(contextMap -> contextMap.get("global"))
        .map(PrefabContextFactory::from)
        .map(PrefabContextSet::convert)
    );
    this.dataNode = dataNode;
    this.expectedDataNode = expectedDataNode;
  }

  private static Prefab.ContextShape apply(JsonNode shapeNode) {
    String contextName = shapeNode.get("name").asText();
    JsonNode fieldTypesArrayNode = shapeNode.get("field_types");
    Prefab.ContextShape.Builder shapeBuilder = Prefab.ContextShape
      .newBuilder()
      .setName(contextName);
    fieldTypesArrayNode
      .fields()
      .forEachRemaining(keyValueEntry -> {
        shapeBuilder.putFieldTypes(
          keyValueEntry.getKey(),
          keyValueEntry.getValue().asInt()
        );
      });
    return shapeBuilder.build();
  }

  @Override
  protected void performVerification(PrefabCloudClient prefabCloudClient) {
    // build context from data
    PrefabContextSet contextSet = buildContextSetFromObjectDataNode(dataNode);
    assertThat(expectedDataNode.isArray())
      .as("expected data node should be an array")
      .isTrue();
    List<Prefab.ContextShape> expectedShapes = buildExpectedShapesFromExpectedDataNode();
    prefabCloudClient.configClient().get("my-test-key", contextSet);
    TelemetryAccumulator telemetryAccumulator = getTelemetryAccumulator(
      prefabCloudClient
    );

    await()
      .atMost(Duration.of(3, ChronoUnit.SECONDS))
      .untilAsserted(() -> {
        List<Prefab.ContextShape> actualShapes = telemetryAccumulator
          .getTelemetryEventsList()
          .stream()
          .flatMap(t -> t.getEventsList().stream())
          .filter(Prefab.TelemetryEvent::hasContextShapes)
          .map(Prefab.TelemetryEvent::getContextShapes)
          .flatMap(c -> c.getShapesList().stream())
          .filter(contextShape -> !contextShape.getName().equals("prefab-api-key"))
          .collect(Collectors.toList());
        assertThat(actualShapes).containsExactlyInAnyOrderElementsOf(expectedShapes);
      });
  }

  private List<Prefab.ContextShape> buildExpectedShapesFromExpectedDataNode() {
    return StreamSupport
      .stream(expectedDataNode.spliterator(), false)
      .map(ContextShapeIntegrationTestCaseDescriptor::apply)
      .collect(Collectors.toList());
  }
}
