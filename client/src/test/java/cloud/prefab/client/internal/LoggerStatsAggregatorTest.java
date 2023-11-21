package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cloud.prefab.domain.Prefab;
import java.time.Clock;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoggerStatsAggregatorTest {

  static final String LOGGER_A = "logger.a";
  static final String LOGGER_B = "logger.b";

  @Mock
  Clock clock;

  LoggerStatsAggregator instance;

  @BeforeEach
  void beforeEach() {
    when(clock.millis()).thenReturn(1L, 2L, 3L);
    instance = new LoggerStatsAggregator(clock);
  }

  @Test
  void itAccumulates() {
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.TRACE, 1);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.TRACE, 2);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.DEBUG, 1);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.DEBUG, 1);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.DEBUG, 1);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.DEBUG, 4);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.INFO, 5);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.INFO, 6);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.WARN, 7);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.WARN, 8);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.ERROR, 9);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.ERROR, 10);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.FATAL, 21);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.FATAL, 22);

    instance.reportLoggerUsage(LOGGER_B, Prefab.LogLevel.TRACE, 11);
    instance.reportLoggerUsage(LOGGER_B, Prefab.LogLevel.ERROR, 12);

    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.TRACE, 100);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.TRACE, 101);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.DEBUG, 200);
    instance.reportLoggerUsage(LOGGER_A, Prefab.LogLevel.DEBUG, 201);

    instance.reportLoggerUsage(LOGGER_B, Prefab.LogLevel.TRACE, 50);
    instance.reportLoggerUsage(LOGGER_B, Prefab.LogLevel.ERROR, 51);

    LoggerStatsAggregator.LogCounts counts = instance.getAndResetStats();

    assertThat(counts.getStartTime()).isEqualTo(1L);
    assertThat(counts.getLoggerMap())
      .containsOnly(
        MapEntry.entry(
          LOGGER_A,
          Prefab.Logger
            .newBuilder()
            .setLoggerName(LOGGER_A)
            .setTraces(204)
            .setDebugs(408)
            .setInfos(11)
            .setWarns(15)
            .setErrors(19)
            .setFatals(43)
            .build()
        ),
        MapEntry.entry(
          LOGGER_B,
          Prefab.Logger
            .newBuilder()
            .setLoggerName(LOGGER_B)
            .setTraces(61)
            .setErrors(63)
            .build()
        )
      );
    LoggerStatsAggregator.LogCounts moreCounts = instance.getAndResetStats();
    assertThat(moreCounts.getStartTime()).isEqualTo(2);
    assertThat(moreCounts.getLoggerMap()).isEmpty();
  }
}
