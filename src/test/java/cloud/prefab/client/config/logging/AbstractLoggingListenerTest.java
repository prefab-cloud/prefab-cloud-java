package cloud.prefab.client.config.logging;

import cloud.prefab.client.Options;
import cloud.prefab.client.Options.Datasources;
import cloud.prefab.client.PrefabCloudClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractLoggingListenerTest {

  protected abstract void reset();

  protected String specificLoggerName() {
    return "test.logger";
  }

  protected String otherLoggerName() {
    return "other.logger";
  }

  protected PrefabCloudClient clientWithSpecificLogLevel() {
    return new PrefabCloudClient(
      new Options()
        .setPrefabDatasource(Datasources.LOCAL_ONLY)
        .setConfigOverrideDir("src/test/resources/override_directory")
        .setPrefabEnvs(List.of("logging_specific"))
    );
  }

  protected PrefabCloudClient clientWithDefaultLogLevel() {
    return new PrefabCloudClient(
      new Options()
        .setPrefabDatasource(Datasources.LOCAL_ONLY)
        .setConfigOverrideDir("src/test/resources/override_directory")
        .setPrefabEnvs(List.of("logging_default"))
    );
  }

  @BeforeEach
  public void doReset() {
    reset();
  }
}
