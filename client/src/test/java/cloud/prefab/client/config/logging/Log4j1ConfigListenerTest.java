package cloud.prefab.client.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.internal.ConfigClientImpl;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.jupiter.api.Test;

public class Log4j1ConfigListenerTest extends AbstractLoggingListenerTest {

  @Override
  protected void reset() {
    LogManager.resetConfiguration();
  }

  @Test
  public void itSetsSpecificLogLevel() {
    assertThat(LogManager.getLogger(specificLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.DEBUG);
    assertThat(LogManager.getLogger(otherLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.DEBUG);

    Log4j1ConfigListener
      .getInstance()
      .onChange(getSpecificLogLevelEvent(specificLoggerName(), Prefab.LogLevel.WARN));

    assertThat(LogManager.getLogger(specificLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.WARN);

    assertThat(LogManager.getLogger(otherLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.DEBUG);
  }

  @Test
  void itSetsDefaultLogLevel() {
    assertThat(LogManager.getLogger(specificLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.DEBUG);
    assertThat(LogManager.getLogger(otherLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.DEBUG);

    Log4j1ConfigListener
      .getInstance()
      .onChange(getDefaultLogLevelEvent(Prefab.LogLevel.WARN));

    assertThat(LogManager.getLogger(specificLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.WARN);
    assertThat(LogManager.getLogger(otherLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.WARN);
  }
}
