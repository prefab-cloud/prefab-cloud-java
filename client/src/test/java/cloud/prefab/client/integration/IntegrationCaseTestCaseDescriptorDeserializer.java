package cloud.prefab.client.integration;

import cloud.prefab.client.integration.telemetry.ContextShapeIntegrationTestCaseDescriptor;
import cloud.prefab.client.integration.telemetry.EvaluationSummaryIntegrationTestCaseDescriptor;
import cloud.prefab.client.integration.telemetry.ExampleContextIntegrationTestCaseDescriptor;
import cloud.prefab.client.integration.telemetry.LogAggregatorIntegrationTestCaseDescriptor;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntegrationCaseTestCaseDescriptorDeserializer
  extends StdDeserializer<IntegrationTestCaseDescriptorIF> {

  private static final Logger LOG = LoggerFactory.getLogger(
    IntegrationCaseTestCaseDescriptorDeserializer.class
  );

  public IntegrationCaseTestCaseDescriptorDeserializer() {
    this(null);
  }

  public IntegrationCaseTestCaseDescriptorDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public IntegrationTestCaseDescriptorIF deserialize(
    JsonParser jsonParser,
    DeserializationContext deserializationContext
  ) throws IOException, JacksonException {
    JsonNode jsonNode = jsonParser.getCodec().readTree(jsonParser);
    TreeNode aggregatorNameNodeMaybe = jsonNode.path("aggregator");
    if (aggregatorNameNodeMaybe.isMissingNode()) {
      return deserializationContext.readTreeAsValue(
        jsonNode,
        StandardIntegrationTestCaseDescriptor.class
      );
    }

    TextNode aggregatorNameNode = (TextNode) aggregatorNameNodeMaybe;

    Class<? extends IntegrationTestCaseDescriptorIF> testCaseDescriptorClass = deriveClass(
      aggregatorNameNode.asText()
    );

    LOG.info("reading next case as {}", testCaseDescriptorClass);
    return deserializationContext.readTreeAsValue(jsonNode, testCaseDescriptorClass);
  }

  Class<? extends IntegrationTestCaseDescriptorIF> deriveClass(String aggregatorName) {
    switch (aggregatorName) {
      case "log_path":
        return LogAggregatorIntegrationTestCaseDescriptor.class;
      case "context_shape":
        return ContextShapeIntegrationTestCaseDescriptor.class;
      case "evaluation_summary":
        return EvaluationSummaryIntegrationTestCaseDescriptor.class;
      case "example_contexts":
        return ExampleContextIntegrationTestCaseDescriptor.class;
      default:
        throw new IllegalArgumentException(
          String.format("no implementation for aggregator %s", aggregatorName)
        );
    }
  }
}
