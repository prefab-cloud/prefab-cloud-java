package cloud.prefab.client.integration.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.integration.BaseIntegrationTestCaseDescriptor;
import cloud.prefab.client.integration.IntegrationTestCaseDescriptorIF;
import cloud.prefab.client.integration.IntegrationTestClientOverrides;
import cloud.prefab.client.integration.TelemetryAccumulator;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.domain.Prefab;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.MoreObjects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TelemetryIntegrationTestCaseDescriptor
  extends BaseIntegrationTestCaseDescriptor
  implements IntegrationTestCaseDescriptorIF {

  private static final Logger LOG = LoggerFactory.getLogger(
    TelemetryIntegrationTestCaseDescriptor.class
  );

  public TelemetryIntegrationTestCaseDescriptor(
    String name,
    IntegrationTestClientOverrides clientOverrides,
    Optional<PrefabContextSet> globalContext
  ) {
    super(
      name,
      MoreObjects.firstNonNull(clientOverrides, IntegrationTestClientOverrides.empty()),
      globalContext
    );
  }

  TelemetryAccumulator getTelemetryAccumulator(PrefabCloudClient prefabCloudClient) {
    return (TelemetryAccumulator) prefabCloudClient
      .getOptions()
      .getTelemetryListener()
      .orElseThrow();
  }

  @Override
  protected void customizeOptions(Options options) {
    super.customizeOptions(options);
    options.setTelemetryListener(new TelemetryAccumulator());
    options.setTelemetryUploadIntervalSeconds(1);
  }

  protected PrefabContext buildContextFromObjectDataNode(
    String contextName,
    JsonNode dataNode
  ) {
    PrefabContext.Builder prefabContextBuilder = PrefabContext.newBuilder(contextName);
    dataNode
      .fields()
      .forEachRemaining(contextKeyAndValue -> {
        String contextPropertyName = contextKeyAndValue.getKey();
        prefabContextBuilder.put(
          contextPropertyName,
          configValueFromJsonNode(contextKeyAndValue.getValue())
        );
      });

    return prefabContextBuilder.build();
  }

  protected PrefabContextSet buildContextSetFromObjectDataNode(JsonNode dataNode) {
    assertThat(dataNode.isObject()).as("data node should be an object").isTrue();
    PrefabContextSet contextSet = new PrefabContextSet();
    dataNode
      .fields()
      .forEachRemaining(keyValuePair ->
        contextSet.addContext(
          buildContextFromObjectDataNode(keyValuePair.getKey(), keyValuePair.getValue())
        )
      );
    return contextSet;
  }

  protected Prefab.ConfigValue configValueFromJsonNode(JsonNode valueNode) {
    switch (valueNode.getNodeType()) {
      case STRING:
        return ConfigValueUtils.from(valueNode.asText());
      case NUMBER:
        switch (valueNode.numberType()) {
          case DOUBLE:
          // fall through
          case FLOAT:
            return ConfigValueUtils.from(valueNode.asDouble());
          case INT:
            return ConfigValueUtils.from(valueNode.asLong());
          default:
            throw new RuntimeException(
              String.format(
                "unexpected number type %s in node %s",
                valueNode.numberType(),
                valueNode
              )
            );
        }
      case BOOLEAN:
        return ConfigValueUtils.from(valueNode.asBoolean());
      case ARRAY:
        // we only handle string lists currently
        return ConfigValueUtils.from(
          StreamSupport
            .stream(valueNode.spliterator(), false)
            .map(JsonNode::asText)
            .collect(Collectors.toList())
        );
      default:
        throw new RuntimeException(
          String.format(
            "unexpected node type %s for node %s",
            valueNode.getNodeType(),
            valueNode
          )
        );
    }
  }
}
