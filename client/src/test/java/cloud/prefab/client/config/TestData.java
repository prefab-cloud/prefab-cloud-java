package cloud.prefab.client.config;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import java.util.List;

public class TestData {

  public enum TestDataConfigSet {
    SPECIFIC_LOGGING("logging_specific"),
    DEFAULT_LOGGING("logging_default");

    private final String environmentName;

    TestDataConfigSet(String environmentName) {
      this.environmentName = environmentName;
    }

    public String getEnvironmentName() {
      return environmentName;
    }
  }

  public static Options getDefaultOptionsWithEnvName(String envName) {
    return new Options()
      .setPrefabDatasource(Options.Datasources.LOCAL_ONLY)
      .setConfigOverrideDir("src/test/resources/override_directory")
      .setPrefabEnvs(List.of(envName));
  }

  /*
  public Options getDefaultOptionsLoggingConfiguration(
    TestDataConfigSet testDataConfigSet
  ) {
    return getDefaultOptionsLoggingConfiguration(testDataConfigSet.getEnvironmentName());
  }

  public static PrefabCloudClient clientWithSpecificLogLevel() {
    return clientWithOptions()
  }

  public static PrefabCloudClient clientWithDefaultLogLevel() {

    return ("logging_default");
  }



 */
  public static PrefabCloudClient clientWithOptions(Options options) {
    return new PrefabCloudClient(options);
  }
}
