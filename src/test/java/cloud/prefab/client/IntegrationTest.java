package cloud.prefab.client;

import cloud.prefab.client.integration.IntegrationTestFile;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

public class IntegrationTest {

  private static final Path INTEGRATION_TEST_DATA_DIRECTORY = Paths.get(
    "src/test/resources/prefab-cloud-integration-test-data"
  );

  private static final ObjectReader YAML_READER = new YAMLMapper()
    .registerModule(new Jdk8Module())
    .reader();

  @TestFactory
  public Collection<DynamicTest> runIntegrationTests() throws IOException {
    if (1 == 1) {
      return Collections.emptyList();
    }
    return findIntegrationTestFiles()
      .stream()
      .map(IntegrationTest::parseTestFile)
      .flatMap(IntegrationTestFile::buildDynamicTests)
      .collect(ImmutableList.toImmutableList());
  }

  private static List<Path> findIntegrationTestFiles() throws IOException {
    try (
      Stream<Path> stream = Files.list(
        INTEGRATION_TEST_DATA_DIRECTORY
          .resolve("tests")
          .resolve(getIntegrationTestsVersion())
      )
    ) {
      return stream.collect(ImmutableList.toImmutableList());
    }
  }

  private static IntegrationTestFile parseTestFile(Path testFile) {
    try {
      return YAML_READER.readValue(testFile.toFile(), IntegrationTestFile.class);
    } catch (IOException e) {
      throw new RuntimeException("Error parsing test file: " + testFile, e);
    }
  }

  private static String getIntegrationTestsVersion() throws IOException {
    return Files.readString(INTEGRATION_TEST_DATA_DIRECTORY.resolve("version")).trim();
  }
}
