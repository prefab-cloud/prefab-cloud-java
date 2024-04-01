package cloud.prefab.client;

import cloud.prefab.client.integration.IntegrationCaseTestCaseDescriptorDeserializer;
import cloud.prefab.client.integration.IntegrationTestCaseDescriptorIF;
import cloud.prefab.client.integration.IntegrationTestFile;
import cloud.prefab.client.integration.IntegrationTestFileContents;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

public class IntegrationTest {

  private static final Path INTEGRATION_TEST_DATA_DIRECTORY = Paths.get(
    "src/test/resources/prefab-cloud-integration-test-data"
  );

  private static final ObjectReader YAML_READER = new YAMLMapper()
    .registerModule(new Jdk8Module())
    .registerModule(
      new SimpleModule() {
        {
          addDeserializer(
            IntegrationTestCaseDescriptorIF.class,
            new IntegrationCaseTestCaseDescriptorDeserializer()
          );
        }
      }
    )
    .reader();

  @TestFactory
  public Collection<DynamicTest> runIntegrationTests() throws IOException {
    return findIntegrationTestFiles()
      .stream()
      .map(IntegrationTest::parseTestFile)
      .flatMap(IntegrationTestFile::buildDynamicTests)
      .collect(Collectors.toList());
  }

  private static List<Path> findIntegrationTestFiles() throws IOException {
    try (
      Stream<Path> stream = Files.list(
        INTEGRATION_TEST_DATA_DIRECTORY.resolve("tests/current")
      )
    ) {
      return stream.collect(Collectors.toList());
    }
  }

  private static IntegrationTestFile parseTestFile(Path testFile) {
    try {
      IntegrationTestFileContents fileContents = YAML_READER.readValue(
        testFile.toFile(),
        IntegrationTestFileContents.class
      );
      return new IntegrationTestFile(fileContents, testFile);
    } catch (IOException e) {
      throw new RuntimeException("Error parsing test file: " + testFile, e);
    }
  }

  private static String getIntegrationTestsVersion() throws IOException {
    return Files.readString(INTEGRATION_TEST_DATA_DIRECTORY.resolve("version")).trim();
  }
}
