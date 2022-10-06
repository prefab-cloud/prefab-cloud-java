package cloud.prefab.client.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.ConfigClient;
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
    new ConfigClient(
      clientWithSpecificLogLevel(),
      JavaUtilLoggingConfigListener.getInstance()
    );

    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.INFO)).isFalse();

    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.INFO)).isTrue();
  }

  @Test
  public void itSetsDefaultLogLevel() {
    new ConfigClient(
      clientWithDefaultLogLevel(),
      JavaUtilLoggingConfigListener.getInstance()
    );

    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(specificLoggerName()).isLoggable(Level.INFO)).isFalse();

    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.WARNING)).isTrue();
    assertThat(Logger.getLogger(otherLoggerName()).isLoggable(Level.INFO)).isFalse();
  }
}
