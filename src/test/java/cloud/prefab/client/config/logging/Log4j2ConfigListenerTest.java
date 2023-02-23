package cloud.prefab.client.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.internal.ConfigClientImpl;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;

public class Log4j2ConfigListenerTest extends AbstractLoggingListenerTest {

  @Override
  protected void reset() {
    Configurator.reconfigure();
  }

  @Test
  public void itSetsSpecificLogLevel() {
    new ConfigClientImpl(
      clientWithSpecificLogLevel(),
      Log4j2ConfigListener.getInstance()
    );

    assertThat(LogManager.getLogger(specificLoggerName()).getLevel())
      .isEqualTo(Level.WARN);

    assertThat(LogManager.getLogger(otherLoggerName()).getLevel()).isEqualTo(Level.ERROR);
  }

  @Test
  public void itSetsDefaultLogLevel() {
    new ConfigClientImpl(clientWithDefaultLogLevel(), Log4j2ConfigListener.getInstance());

    assertThat(LogManager.getLogger(specificLoggerName()).getLevel())
      .isEqualTo(Level.WARN);

    assertThat(LogManager.getLogger(otherLoggerName()).getLevel()).isEqualTo(Level.WARN);
  }
}
