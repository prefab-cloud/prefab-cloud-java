package cloud.prefab.client.integration;

import cloud.prefab.context.PrefabContextSetReadable;
import org.junit.jupiter.api.function.Executable;

public interface IntegrationTestCaseDescriptorIF {
  String getName();

  Executable asExecutable(PrefabContextSetReadable prefabContext);
}
