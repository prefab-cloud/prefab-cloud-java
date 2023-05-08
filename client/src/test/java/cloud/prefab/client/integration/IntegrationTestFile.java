package cloud.prefab.client.integration;

import cloud.prefab.context.PrefabContextSetReadable;
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
      .flatMap(testDescriptor -> {
        PrefabContextSetReadable contextSetReadable = testDescriptor.getPrefabContext();
        return testDescriptor
          .getTestCaseDescriptors()
          .stream()
          .map(testCaseDescriptor -> {
            String displayName = Joiner
              .on(" : ")
              .useForNull("")
              .join(
                version,
                function,
                testDescriptor.getName().orElse(null),
                testCaseDescriptor.getName()
              );
            return DynamicTest.dynamicTest(
              displayName,
              testCaseDescriptor.asExecutable(contextSetReadable)
            );
          });
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
