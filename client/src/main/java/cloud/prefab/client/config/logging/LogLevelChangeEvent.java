package cloud.prefab.client.config.logging;

import cloud.prefab.domain.Prefab;
import java.util.Optional;

public class LogLevelChangeEvent {

  private final String loggerName;

  private final Optional<Prefab.LogLevel> previousLevel;

  private final Optional<Prefab.LogLevel> newLevel;

  public LogLevelChangeEvent(
    String loggerName,
    Optional<Prefab.LogLevel> previousLevel,
    Optional<Prefab.LogLevel> newLevel
  ) {
    this.loggerName = loggerName;
    this.previousLevel = previousLevel;
    this.newLevel = newLevel;
  }

  public String getLoggerName() {
    return loggerName;
  }

  public Optional<Prefab.LogLevel> getPreviousLevel() {
    return previousLevel;
  }

  public Optional<Prefab.LogLevel> getNewLevel() {
    return newLevel;
  }
}
