package cloud.prefab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.domain.Prefab;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigClientTest {

  @Test
  void localModeUnlocks() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options().setPrefabDatasource(Options.Datasources.LOCAL_ONLY)
    );
    ConfigClient configClient = new ConfigClientImpl(baseClient);

    final Optional<Prefab.ConfigValue> key = configClient.get("key");
    assertThat(key).isNotPresent();
  }

  @Test
  void initializationTimeout() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options()
        .setApikey("0-P1-E1-SDK-1234-123-23")
        .setInitializationTimeoutSec(1)
        .setOnInitializationFailure(Options.OnInitializationFailure.RAISE)
    );

    ConfigClient configClient = new ConfigClientImpl(baseClient);
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

    ConfigClient configClient = new ConfigClientImpl(baseClient);
    assertThat(configClient.get("key")).isNotPresent();
  }

  @Test
  void broadcast() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options()
        .setApikey("0-P1-E1-SDK-1234-123-23")
        .setConfigOverrideDir("none")
        .setInitializationTimeoutSec(1)
        .setOnInitializationFailure(Options.OnInitializationFailure.UNLOCK)
    );

    ConfigClient configClient = new ConfigClientImpl(baseClient);

    List<ConfigChangeEvent> receivedEvents = new ArrayList<>();
    ConfigChangeListener listener = receivedEvents::add;

    configClient.addConfigChangeListener(listener);

    assertThat(configClient.get("key")).isNotPresent();

    assertThat(receivedEvents)
      .containsExactlyInAnyOrder(
        new ConfigChangeEvent(
          "sample_bool",
          Optional.empty(),
          Optional.of(Prefab.ConfigValue.newBuilder().setBool(true).build())
        ),
        new ConfigChangeEvent(
          "sample",
          Optional.empty(),
          Optional.of(
            Prefab.ConfigValue.newBuilder().setString("default sample value").build()
          )
        )
      );
  }
}
