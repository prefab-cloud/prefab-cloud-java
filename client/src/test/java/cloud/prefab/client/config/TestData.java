package cloud.prefab.client.config;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import java.util.List;

public class TestData {

  public static PrefabCloudClient clientWithSpecificLogLevel() {
    return clientWithEnv("logging_specific");
  }

  public static PrefabCloudClient clientWithDefaultLogLevel() {
    return clientWithEnv("logging_default");
  }

  public static PrefabCloudClient clientWithEnv(String envName) {
    return new PrefabCloudClient(
      new Options()
        .setPrefabDatasource(Options.Datasources.LOCAL_ONLY)
        .setConfigOverrideDir("src/test/resources/override_directory")
        .setPrefabEnvs(List.of(envName))
    );
  }
}
