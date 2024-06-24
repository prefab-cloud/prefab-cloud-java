package cloud.prefab.client.integration;

import org.junit.jupiter.api.function.Executable;

public interface IntegrationTestCaseDescriptorIF {
  String getName();

  Executable asExecutable();
}
