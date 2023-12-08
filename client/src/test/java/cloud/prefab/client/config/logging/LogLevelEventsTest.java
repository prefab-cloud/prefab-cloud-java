package cloud.prefab.client.config.logging;

import static org.mockito.Mockito.verify;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.config.TestData;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
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
    try (
      PrefabCloudClient prefabCloudClient = new PrefabCloudClient(
        TestData
          .getDefaultOptionsWithEnvName(
            TestData.TestDataConfigSet.SPECIFIC_LOGGING.getEnvironmentName()
          )
          .addLogLevelChangeListener(testListener)
      )
    ) {
      prefabCloudClient.configClient();
      verify(testListener).setLevel("test.logger", Optional.of(Level.WARNING));
    }
  }

  @Test
  void itGetsDefaultLevelMessage() {
    try (
      PrefabCloudClient prefabCloudClient = new PrefabCloudClient(
        TestData
          .getDefaultOptionsWithEnvName(
            TestData.TestDataConfigSet.DEFAULT_LOGGING.getEnvironmentName()
          )
          .addLogLevelChangeListener(testListener)
      )
    ) {
      prefabCloudClient.configClient();
      verify(testListener).setDefaultLevel(Optional.of(Level.WARNING));
    }
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
