package cloud.prefab.client.integration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class IntegrationTestFileContents {

  private final String version;
  private final String function;
  private final List<IntegrationTestDescriptor> tests;

  @JsonCreator
  public IntegrationTestFileContents(
    @JsonProperty("version") String version,
    @JsonProperty("function") String function,
    @JsonProperty("tests") List<IntegrationTestDescriptor> tests
  ) {
    this.version = version;
    this.function = function;
    this.tests = tests;
  }

  public String getVersion() {
    return version;
  }

  public String getFunction() {
    return function;
  }

  public List<IntegrationTestDescriptor> getTests() {
    return tests;
  }
}
