package cloud.prefab.client.integration;

import com.google.common.base.Joiner;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class IntegrationTestFile {

  private final IntegrationTestFileContents contents;
  private final Path path;

  public IntegrationTestFile(IntegrationTestFileContents contents, Path path) {
    this.contents = contents;
    this.path = path;
  }

  public String getVersion() {
    return contents.getVersion();
  }

  public String getFunction() {
    return contents.getFunction();
  }

  public List<IntegrationTestDescriptor> getTests() {
    return contents.getTests();
  }

  public Stream<DynamicTest> buildDynamicTests() {
    return getTests()
      .stream()
      .flatMap(testDescriptor ->
        testDescriptor
          .getTestCaseDescriptors()
          .stream()
          .map(testCaseDescriptor -> {
            String displayName = Joiner
              .on(" : ")
              .useForNull("")
              .join(
                getVersion(),
                getFunction(),
                path.getFileName().toString(),
                testDescriptor.getName().orElse(null),
                testCaseDescriptor.getName()
              );
            return DynamicTest.dynamicTest(
              displayName,
              testCaseDescriptor.asExecutable()
            );
          })
      );
  }
}
