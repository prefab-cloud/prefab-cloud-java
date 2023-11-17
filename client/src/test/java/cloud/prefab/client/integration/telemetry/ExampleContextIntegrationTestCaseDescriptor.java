package cloud.prefab.client.integration.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.integration.IntegrationTestClientOverrides;
import cloud.prefab.client.integration.IntegrationTestFunction;
import cloud.prefab.client.integration.TelemetryAccumulator;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.domain.Prefab;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.MoreObjects;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleContextIntegrationTestCaseDescriptor
  extends TelemetryIntegrationTestCaseDescriptor {

  private static final Logger LOG = LoggerFactory.getLogger(
    ExampleContextIntegrationTestCaseDescriptor.class
  );
  private final JsonNode dataNode;
  private final JsonNode expectedDataNode;

  @JsonCreator
  public ExampleContextIntegrationTestCaseDescriptor(
    @JsonProperty("name") String name,
    @JsonProperty("client") String client,
    @JsonProperty("client_overrides") IntegrationTestClientOverrides clientOverrides,
    @JsonProperty("endpoint") String endpoint,
    @JsonProperty("function") IntegrationTestFunction function,
    @JsonProperty("aggregator") String aggregator,
    @JsonProperty("data") JsonNode dataNode,
    @JsonProperty("expected_data") JsonNode expectedDataNode
  ) {
    super(
      name,
      MoreObjects.firstNonNull(clientOverrides, IntegrationTestClientOverrides.empty())
    );
    this.dataNode = dataNode;
    this.expectedDataNode = expectedDataNode;
  }

  @Override
  protected void customizeOptions(Options options) {
    super.customizeOptions(options);
    options.setContextUploadMode(Options.CollectContextMode.PERIODIC_EXAMPLE);
  }

  @Override
  protected void performVerification(PrefabCloudClient prefabCloudClient) {
    PrefabContextSet contextSetToSend = buildContextFromArrayDataNode(dataNode);
    PrefabContextSet expectedContextSet = buildExpectedContextSet(expectedDataNode);
    LOG.info("context set = {}", contextSetToSend);
    LOG.info("expected context set = {}", expectedContextSet);

    prefabCloudClient.configClient().get("my-test-key", contextSetToSend);
    TelemetryAccumulator telemetryAccumulator = getTelemetryAccumulator(
      prefabCloudClient
    );
    await()
      .atMost(Duration.of(3, ChronoUnit.SECONDS))
      .untilAsserted(() -> {
        List<Prefab.Context> actualContexts = telemetryAccumulator
          .getTelemetryEventsList()
          .stream()
          .flatMap(t -> t.getEventsList().stream())
          .filter(Prefab.TelemetryEvent::hasExampleContexts)
          .map(Prefab.TelemetryEvent::getExampleContexts)
          .flatMap(c -> c.getExamplesList().stream())
          .flatMap(e -> e.getContextSet().getContextsList().stream())
          .collect(Collectors.toList());

        assertThat(actualContexts)
          .containsExactlyInAnyOrderElementsOf(
            expectedContextSet.toProto().getContextsList()
          );

        LOG.info("Actual contexts were {}", actualContexts);
      });
  }

  PrefabContextSet buildExpectedContextSet(JsonNode dataNode) {
    assertThat(dataNode.isArray()).as("expected data node should be an array").isTrue();
    PrefabContextSet contextSet = new PrefabContextSet();
    dataNode.forEach(arrayElement -> {
      arrayElement
        .fields()
        .forEachRemaining(keyValuePair -> {
          // the 0.2.2 version has ambiguous representation so there can be more than one in each array element
          String contextName = keyValuePair.getKey();
          PrefabContext.Builder builder = PrefabContext.newBuilder(contextName);
          JsonNode contextFieldsArray = keyValuePair.getValue();
          contextFieldsArray.forEach(contextFieldsArrayElement ->
            builder.put(
              contextFieldsArrayElement.get("key").asText(),
              configValueFromJsonNode(contextFieldsArrayElement.get("value"))
            )
          );
          contextSet.addContext(builder.build());
        });
    });

    return contextSet;
  }

  PrefabContextSet buildContextFromArrayDataNode(JsonNode dataNode) {
    assertThat(dataNode.isArray()).as("datanode should be an array").isTrue();
    PrefabContextSet contextSet = new PrefabContextSet();
    dataNode.forEach(arrayMemberNode -> {
      assertThat(arrayMemberNode.isObject()).as("Array members should be an object");
      // this is a single key object node
      arrayMemberNode
        .fields()
        .forEachRemaining(keyValuePair ->
          contextSet.addContext(
            buildContextFromObjectDataNode(keyValuePair.getKey(), keyValuePair.getValue())
          )
        );
    });
    return contextSet;
  }
}
