package cloud.prefab.client.integration;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
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
    TreeNode aggregatorNameNode = jsonNode.path("aggregator");
    if (aggregatorNameNode.isMissingNode()) {
      LOG.info("no aggregator present, falling back to default deserialization");
      return deserializationContext.readTreeAsValue(
        jsonNode,
        StandardIntegrationTestCaseDescriptor.class
      );
    }
    throw deserializationContext.mappingException(IntegrationTestCaseDescriptorIF.class);
  }
}
