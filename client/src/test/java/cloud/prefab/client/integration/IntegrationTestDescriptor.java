package cloud.prefab.client.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;

public class IntegrationTestDescriptor {

  private final Optional<String> name;
  private final List<IntegrationTestCaseDescriptorIF> testCaseDescriptors;

  public IntegrationTestDescriptor(
    @JsonProperty("name") Optional<String> name,
    @JsonProperty("cases") List<IntegrationTestCaseDescriptorIF> testCaseDescriptors
  ) {
    this.name = name;
    this.testCaseDescriptors = testCaseDescriptors;
  }

  public List<IntegrationTestCaseDescriptorIF> getTestCaseDescriptors() {
    return testCaseDescriptors;
  }

  public Optional<String> getName() {
    return name;
  }
}
