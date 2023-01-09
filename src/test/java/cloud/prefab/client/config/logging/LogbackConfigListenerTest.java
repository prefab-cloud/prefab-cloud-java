package cloud.prefab.client.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import cloud.prefab.client.ConfigClient;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class LogbackConfigListenerTest extends AbstractLoggingListenerTest {

  @Override
  protected void reset() {
    ((LoggerContext) LoggerFactory.getILoggerFactory()).reset();
  }

  @Test
  public void itSetsSpecificLogLevel() {
    new ConfigClient(clientWithSpecificLogLevel(), LogbackConfigListener.getInstance());

    assertThat(
      ((Logger) LoggerFactory.getLogger(specificLoggerName())).getEffectiveLevel()
    )
      .isEqualTo(Level.WARN);

    assertThat(((Logger) LoggerFactory.getLogger(otherLoggerName())).getEffectiveLevel())
      .isEqualTo(Level.DEBUG);
  }

  @Test
  public void itSetsDefaultLogLevel() {
    new ConfigClient(clientWithDefaultLogLevel(), LogbackConfigListener.getInstance());

    assertThat(
      ((Logger) LoggerFactory.getLogger(specificLoggerName())).getEffectiveLevel()
    )
      .isEqualTo(Level.WARN);

    assertThat(((Logger) LoggerFactory.getLogger(otherLoggerName())).getEffectiveLevel())
      .isEqualTo(Level.WARN);
  }
}