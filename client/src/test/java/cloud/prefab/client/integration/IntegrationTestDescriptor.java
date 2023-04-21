package cloud.prefab.client.integration;

import cloud.prefab.context.PrefabContextSetReadable;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class IntegrationTestDescriptor {

  private final Map<String, Map<String, Object>> context;
  private final List<IntegrationTestCaseDescriptor> testCaseDescriptors;

  public IntegrationTestDescriptor(
    @JsonProperty("context") Map<String, Map<String, Object>> context,
    @JsonProperty("cases") List<IntegrationTestCaseDescriptor> testCaseDescriptors
  ) {
    this.context = context;
    this.testCaseDescriptors = testCaseDescriptors;
  }

  public Map<String, Map<String, Object>> getContext() {
    return context;
  }

  public PrefabContextSetReadable getPrefabContext() {
    return PrefabContextFactory.from(getContext());
  }

  public List<IntegrationTestCaseDescriptor> getTestCaseDescriptors() {
    return testCaseDescriptors;
  }
}
