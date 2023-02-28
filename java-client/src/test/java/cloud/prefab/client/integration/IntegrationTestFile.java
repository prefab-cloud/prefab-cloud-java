package cloud.prefab.client.integration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class IntegrationTestFile {

  private final String version;
  private final String function;
  private final List<IntegrationTestDescriptor> tests;

  @JsonCreator
  public IntegrationTestFile(
    @JsonProperty("version") String version,
    @JsonProperty("function") String function,
    @JsonProperty("tests") List<IntegrationTestDescriptor> tests
  ) {
    this.version = version;
    this.function = function;
    this.tests = tests;
  }

  public Stream<DynamicTest> buildDynamicTests() {
    return tests
      .stream()
      .map(testDescriptor -> {
        String displayName = Joiner
          .on(" : ")
          .join(version, function, testDescriptor.getName());

        return DynamicTest.dynamicTest(displayName, testDescriptor.asExecutable());
      });
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
