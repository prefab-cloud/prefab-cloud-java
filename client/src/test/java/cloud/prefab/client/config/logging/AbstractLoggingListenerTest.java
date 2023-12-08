package cloud.prefab.client.config.logging;

import cloud.prefab.domain.Prefab;
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

  protected LogLevelChangeEvent getDefaultLogLevelEvent(Prefab.LogLevel level) {
    return buildLogLevelEvent(
      AbstractLoggingListener.LOG_LEVEL_PREFIX,
      Optional.of(level)
    );
  }

  protected LogLevelChangeEvent getDefaultLogLevelEvent(Optional<Prefab.LogLevel> level) {
    return buildLogLevelEvent(AbstractLoggingListener.LOG_LEVEL_PREFIX, level);
  }

  protected LogLevelChangeEvent getSpecificLogLevelEvent(
    String loggerName,
    Prefab.LogLevel level
  ) {
    return getSpecificLogLevelEvent(loggerName, Optional.of(level));
  }

  protected LogLevelChangeEvent getSpecificLogLevelEvent(
    String loggerName,
    Optional<Prefab.LogLevel> level
  ) {
    return buildLogLevelEvent(
      AbstractLoggingListener.LOG_LEVEL_PREFIX + "." + loggerName,
      level
    );
  }

  private LogLevelChangeEvent buildLogLevelEvent(
    String key,
    Optional<Prefab.LogLevel> levelMaybe
  ) {
    return new LogLevelChangeEvent(key, Optional.empty(), levelMaybe);
  }

  @BeforeEach
  public void doReset() {
    reset();
  }
}
