package cloud.prefab.client.config.logging;

import static org.mockito.Mockito.verify;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.internal.ConfigClientImpl;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LogLevelEventsTest {

  // This tests that a given prefab on-disk configuration results in expected calls to the setDefaultLevel and setLevel methods

  @Spy
  TestListener testListener;

  @Test
  void itGetsSpecificLevelMessage() {
    new ConfigClientImpl(clientWithSpecificLogLevel(), testListener);
    verify(testListener).setLevel("test.logger", Optional.of(Level.WARNING));
  }

  @Test
  void itGetsDefaultLevelMessage() {
    new ConfigClientImpl(clientWithDefaultLogLevel(), testListener);
    verify(testListener).setDefaultLevel(Optional.of(Level.WARNING));
  }

  protected PrefabCloudClient clientWithSpecificLogLevel() {
    return new PrefabCloudClient(
      new Options()
        .setPrefabDatasource(Options.Datasources.LOCAL_ONLY)
        .setConfigOverrideDir("src/test/resources/override_directory")
        .setPrefabEnvs(List.of("logging_specific"))
    );
  }

  protected PrefabCloudClient clientWithDefaultLogLevel() {
    return new PrefabCloudClient(
      new Options()
        .setPrefabDatasource(Options.Datasources.LOCAL_ONLY)
        .setConfigOverrideDir("src/test/resources/override_directory")
        .setPrefabEnvs(List.of("logging_default"))
    );
  }

  private static class TestListener extends AbstractLoggingListener {

    @Override
    protected Map getValidLevels() {
      return LEVEL_MAP;
    }

    @Override
    protected void setDefaultLevel(Optional level) {}

    @Override
    protected void setLevel(String loggerName, Optional level) {}

    private static final Map<Prefab.LogLevel, Level> LEVEL_MAP = ImmutableMap
      .<Prefab.LogLevel, Level>builder()
      .put(Prefab.LogLevel.FATAL, Level.SEVERE)
      .put(Prefab.LogLevel.ERROR, Level.SEVERE)
      .put(Prefab.LogLevel.WARN, Level.WARNING)
      .put(Prefab.LogLevel.INFO, Level.INFO)
      .put(Prefab.LogLevel.DEBUG, Level.FINE)
      .put(Prefab.LogLevel.TRACE, Level.FINER)
      .build();
  }
}
