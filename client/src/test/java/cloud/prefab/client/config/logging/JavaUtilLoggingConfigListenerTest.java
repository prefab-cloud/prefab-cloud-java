package cloud.prefab.client.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.internal.ConfigClientImpl;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

public class JavaUtilLoggingConfigListenerTest extends AbstractLoggingListenerTest {

  @Override
  protected void reset() {
    LogManager.getLogManager().reset();
  }

  @Test
  public void itSetsSpecificLogLevel() {
    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.INFO)).isTrue();
    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.INFO)).isTrue();

    JavaUtilLoggingConfigListener
      .getInstance()
      .onChange(getSpecificLogLevelEvent(specificLoggerName(), Prefab.LogLevel.WARN));

    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.INFO)).isFalse();
    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.INFO)).isTrue();
  }

  @Test
  public void itSetsDefaultLogLevel() {
    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.INFO)).isTrue();
    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.INFO)).isTrue();

    JavaUtilLoggingConfigListener
      .getInstance()
      .onChange(getDefaultLogLevelEvent(Prefab.LogLevel.WARN));

    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.INFO)).isFalse();

    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.INFO)).isFalse();
  }
}
