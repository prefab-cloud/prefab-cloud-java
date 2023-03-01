package cloud.prefab.client.config.logging;

import cloud.prefab.client.Options;
import cloud.prefab.client.Options.Datasources;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.domain.Prefab;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractLoggingListenerTest {

  protected abstract void reset();

  protected String specificLoggerName() {
    return "test.logger";
  }

  protected String otherLoggerName() {
    return "other.logger";
  }

  protected ConfigChangeEvent getDefaultLogLevelEvent(Prefab.LogLevel level) {
    return buildLogLevelEvent(
      AbstractLoggingListener.LOG_LEVEL_PREFIX,
      Optional.of(level)
    );
  }

  protected ConfigChangeEvent getDefaultLogLevelEvent(Optional<Prefab.LogLevel> level) {
    return buildLogLevelEvent(AbstractLoggingListener.LOG_LEVEL_PREFIX, level);
  }

  protected ConfigChangeEvent getSpecificLogLevelEvent(
    String loggerName,
    Prefab.LogLevel level
  ) {
    return getSpecificLogLevelEvent(loggerName, Optional.of(level));
  }

  protected ConfigChangeEvent getSpecificLogLevelEvent(
    String loggerName,
    Optional<Prefab.LogLevel> level
  ) {
    return buildLogLevelEvent(
      AbstractLoggingListener.LOG_LEVEL_PREFIX + "." + loggerName,
      level
    );
  }

  private ConfigChangeEvent buildLogLevelEvent(
    String key,
    Optional<Prefab.LogLevel> levelMaybe
  ) {
    return new ConfigChangeEvent(
      key,
      Optional.empty(),
      levelMaybe.map(level -> Prefab.ConfigValue.newBuilder().setLogLevel(level).build())
    );
  }

  @BeforeEach
  public void doReset() {
    reset();
  }
}
