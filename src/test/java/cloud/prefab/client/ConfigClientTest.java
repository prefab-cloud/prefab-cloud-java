package cloud.prefab.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigClientTest {

  @Test
  void localModeUnlocks() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options().setPrefabDatasource(Options.Datasources.LOCAL_ONLY)
    );
    ConfigClient configClient = new ConfigClient(baseClient);

    final Optional<Prefab.ConfigValue> key = configClient.get("key");
    assert (key.isEmpty());
  }

  @Test
  void initializationTimeout() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options()
        .setApikey("0-P1-E1-SDK-1234-123-23")
        .setInitializationTimeoutSec(1)
        .setOnInitializationFailure(Options.OnInitializationFailure.RAISE)
    );

    ConfigClient configClient = new ConfigClient(baseClient);
    assertThrows(
      PrefabInitializationTimeoutException.class,
      () -> configClient.get("key")
    );
  }

  @Test
  void initializationUnlock() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options()
        .setApikey("0-P1-E1-SDK-1234-123-23")
        .setInitializationTimeoutSec(1)
        .setOnInitializationFailure(Options.OnInitializationFailure.UNLOCK)
    );

    ConfigClient configClient = new ConfigClient(baseClient);
    assertEquals(false, configClient.get("key").isPresent());
  }
}
