package cloud.prefab.client.config.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.spi.FilterReply;
import cloud.prefab.client.ConfigClient;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextHelper;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class PrefabContextTurboFilterTest {

  @Mock
  ConfigClient configClient;

  @InjectMocks
  PrefabContextTurboFilter logbackTurboFilter;

  @Test
  void itReportsLoggingAndAsksForLogLevelReturnsNeutral() {
    Logger logger = (Logger) LoggerFactory.getLogger(
      "com.example.factory.FactoryFactory"
    );
    when(configClient.isReady()).thenReturn(true);

    when(configClient.getLogLevel(logger.getName())).thenReturn(Optional.empty());

    assertThat(
      logbackTurboFilter.decide(null, logger, Level.DEBUG, "", new Object[0], null)
    )
      .isEqualTo(FilterReply.NEUTRAL);

    Mockito
      .verify(configClient)
      .reportLoggerUsage(logger.getName(), Prefab.LogLevel.DEBUG, 1);
  }

  @Test
  void itReportsLoggingAndAsksForLogLevelReturnsAccept() {
    Logger logger = (Logger) LoggerFactory.getLogger(
      "com.example.factory.FactoryFactory"
    );
    when(configClient.isReady()).thenReturn(true);

    when(configClient.getLogLevel(logger.getName()))
      .thenReturn(Optional.of(Prefab.LogLevel.DEBUG));

    assertThat(
      logbackTurboFilter.decide(null, logger, Level.DEBUG, "", new Object[0], null)
    )
      .isEqualTo(FilterReply.ACCEPT);

    Mockito
      .verify(configClient)
      .reportLoggerUsage(logger.getName(), Prefab.LogLevel.DEBUG, 1);
  }

  @Test
  void itReportsLoggingAndAsksForLogLevelReturnsDeny() {
    Logger logger = (Logger) LoggerFactory.getLogger(
      "com.example.factory.FactoryFactory"
    );
    when(configClient.isReady()).thenReturn(true);

    when(configClient.getLogLevel(logger.getName()))
      .thenReturn(Optional.of(Prefab.LogLevel.WARN));

    assertThat(
      logbackTurboFilter.decide(null, logger, Level.DEBUG, "", new Object[0], null)
    )
      .isEqualTo(FilterReply.DENY);

    Mockito
      .verify(configClient)
      .reportLoggerUsage(logger.getName(), Prefab.LogLevel.DEBUG, 1);
  }

  @Test
  void itEarlyOutsIfClientUnready() {
    Logger logger = (Logger) LoggerFactory.getLogger(
      "com.example.factory.FactoryFactory"
    );
    when(configClient.isReady()).thenReturn(false);
    assertThat(
      logbackTurboFilter.decide(null, logger, Level.DEBUG, "", new Object[0], null)
    )
      .isEqualTo(FilterReply.NEUTRAL);
    verifyNoMoreInteractions(configClient);
  }
}
