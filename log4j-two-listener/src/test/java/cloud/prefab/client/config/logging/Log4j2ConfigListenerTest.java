package cloud.prefab.client.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.domain.Prefab;
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
    assertThat(LogManager.getLogger(specificLoggerName()).getLevel())
      .isEqualTo(Level.ERROR);

    assertThat(LogManager.getLogger(otherLoggerName()).getLevel()).isEqualTo(Level.ERROR);

    Log4j2ConfigListener
      .getInstance()
      .onChange(getSpecificLogLevelEvent(specificLoggerName(), Prefab.LogLevel.WARN));

    assertThat(LogManager.getLogger(specificLoggerName()).getLevel())
      .isEqualTo(Level.WARN);

    assertThat(LogManager.getLogger(otherLoggerName()).getLevel()).isEqualTo(Level.ERROR);
  }

  @Test
  public void itSetsDefaultLogLevel() {
    assertThat(LogManager.getLogger(specificLoggerName()).getLevel())
      .isEqualTo(Level.ERROR);

    assertThat(LogManager.getLogger(otherLoggerName()).getLevel()).isEqualTo(Level.ERROR);
    Log4j2ConfigListener
      .getInstance()
      .onChange(getDefaultLogLevelEvent(Prefab.LogLevel.WARN));

    assertThat(LogManager.getLogger(specificLoggerName()).getLevel())
      .isEqualTo(Level.WARN);

    assertThat(LogManager.getLogger(otherLoggerName()).getLevel()).isEqualTo(Level.WARN);
  }
}
