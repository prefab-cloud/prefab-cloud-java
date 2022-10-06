package cloud.prefab.client.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.ConfigClient;
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
    new ConfigClient(clientWithSpecificLogLevel(), Log4j1ConfigListener.getInstance());

    assertThat(LogManager.getLogger(specificLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.WARN);

    assertThat(LogManager.getLogger(otherLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.DEBUG);
  }

  @Test
  public void itSetsDefaultLogLevel() {
    new ConfigClient(clientWithDefaultLogLevel(), Log4j1ConfigListener.getInstance());

    assertThat(LogManager.getLogger(specificLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.WARN);

    assertThat(LogManager.getLogger(otherLoggerName()).getEffectiveLevel())
      .isEqualTo(Level.WARN);
  }
}
