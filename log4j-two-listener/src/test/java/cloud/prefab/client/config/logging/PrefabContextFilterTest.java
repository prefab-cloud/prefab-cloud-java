package cloud.prefab.client.config.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class PrefabContextFilterTest {

  @Mock
  ConfigClient configClient;

  @InjectMocks
  PrefabContextFilter prefabContextFilter;

  Logger testLogger = (Logger) LoggerFactory.getLogger(
    "com.example.factory.FactoryFactory"
  );

  @Test
  void itReportsLoggingAndAsksForLogLevelReturnsNeutral() {
    when(configClient.isReady()).thenReturn(true);

    when(configClient.getLogLevel(testLogger.getName())).thenReturn(Optional.empty());

    assertThat(prefabContextFilter.decide(testLogger.getName(), Level.DEBUG))
      .isEqualTo(Filter.Result.NEUTRAL);

    verify(configClient)
      .reportLoggerUsage(testLogger.getName(), Prefab.LogLevel.DEBUG, 1);
  }

  @Test
  void itReportsLoggingAndAsksForLogLevelReturnsAccept() {
    when(configClient.isReady()).thenReturn(true);

    when(configClient.getLogLevel(testLogger.getName()))
      .thenReturn(Optional.of(Prefab.LogLevel.DEBUG));

    assertThat(prefabContextFilter.decide(testLogger.getName(), Level.DEBUG))
      .isEqualTo(Filter.Result.ACCEPT);

    verify(configClient)
      .reportLoggerUsage(testLogger.getName(), Prefab.LogLevel.DEBUG, 1);
  }

  @Test
  void itReportsLoggingAndAsksForLogLevelReturnsDeny() {
    when(configClient.isReady()).thenReturn(true);

    when(configClient.getLogLevel(testLogger.getName()))
      .thenReturn(Optional.of(Prefab.LogLevel.WARN));

    assertThat(prefabContextFilter.decide(testLogger.getName(), Level.DEBUG))
      .isEqualTo(Filter.Result.DENY);

    verify(configClient)
      .reportLoggerUsage(testLogger.getName(), Prefab.LogLevel.DEBUG, 1);
  }

  @Test
  void itEarlyOutsIfClientUnready() {
    when(configClient.isReady()).thenReturn(false);
    assertThat(prefabContextFilter.decide(testLogger.getName(), Level.DEBUG))
      .isEqualTo(Filter.Result.NEUTRAL);
    verifyNoMoreInteractions(configClient);
  }
}
