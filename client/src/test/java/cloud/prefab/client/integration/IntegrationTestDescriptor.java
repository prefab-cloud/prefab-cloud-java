package cloud.prefab.client.integration;

import cloud.prefab.context.PrefabContextSetReadable;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class IntegrationTestDescriptor {

  private final Optional<String> name;
  private final Map<String, Map<String, Object>> context;
  private final List<IntegrationTestCaseDescriptorIF> testCaseDescriptors;

  public IntegrationTestDescriptor(
    @JsonProperty("name") Optional<String> name,
    @JsonProperty("context") Map<String, Map<String, Object>> context,
    @JsonProperty("cases") List<IntegrationTestCaseDescriptorIF> testCaseDescriptors
  ) {
    this.name = name;
    this.context = context;
    this.testCaseDescriptors = testCaseDescriptors;
  }

  public Map<String, Map<String, Object>> getContext() {
    return context;
  }

  public PrefabContextSetReadable getPrefabContext() {
    return PrefabContextFactory.from(getContext());
  }

  public List<IntegrationTestCaseDescriptorIF> getTestCaseDescriptors() {
    return testCaseDescriptors;
  }

  public Optional<String> getName() {
    return name;
  }
}
