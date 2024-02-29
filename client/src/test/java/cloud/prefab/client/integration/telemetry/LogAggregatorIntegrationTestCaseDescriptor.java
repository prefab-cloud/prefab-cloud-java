package cloud.prefab.client.integration.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.integration.IntegrationTestClientOverrides;
import cloud.prefab.client.integration.IntegrationTestFunction;
import cloud.prefab.client.integration.TelemetryAccumulator;
import cloud.prefab.domain.Prefab;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableBiMap;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogAggregatorIntegrationTestCaseDescriptor
  extends TelemetryIntegrationTestCaseDescriptor {

  private static final Logger LOG = LoggerFactory.getLogger(
    LogAggregatorIntegrationTestCaseDescriptor.class
  );
  private final List<LoggerNamesAndCounts> inputData;

  private final List<LoggerNamesAndCounts> expectedData;

  private static final ImmutableBiMap<String, Prefab.LogLevel> LOG_LEVEL_LOOKUP = ImmutableBiMap.of(
    "debugs",
    Prefab.LogLevel.DEBUG,
    "infos",
    Prefab.LogLevel.INFO,
    "warns",
    Prefab.LogLevel.WARN,
    "errors",
    Prefab.LogLevel.ERROR
  );

  @JsonCreator
  public LogAggregatorIntegrationTestCaseDescriptor(
    @JsonProperty("name") String name,
    @JsonProperty("client") String client,
    @JsonProperty("client_overrides") IntegrationTestClientOverrides clientOverrides,
    @JsonProperty("endpoint") String endpoint,
    @JsonProperty("function") IntegrationTestFunction function,
    @JsonProperty("aggregator") String aggregator,
    @JsonProperty("data") List<LoggerNamesAndCounts> inputData,
    @JsonProperty("expected_data") List<LoggerNamesAndCounts> expectedData
  ) {
    super(
      name,
      MoreObjects.firstNonNull(clientOverrides, IntegrationTestClientOverrides.empty())
    );
    this.inputData = inputData;
    this.expectedData = expectedData;
  }

  @Override
  protected void performVerification(PrefabCloudClient prefabCloudClient) {
    for (LoggerNamesAndCounts inputDatum : inputData) {
      for (Map.Entry<String, Integer> logLevelCount : inputDatum.levelCounts.entrySet()) {
        prefabCloudClient
          .configClient()
          .reportLoggerUsage(
            inputDatum.loggerName,
            LOG_LEVEL_LOOKUP.get(logLevelCount.getKey()),
            logLevelCount.getValue()
          );
      }
    }

    List<Prefab.Logger> expectedLoggers = expectedData
      .stream()
      .map(loggerNamesAndCounts -> {
        Prefab.Logger.Builder builder = Prefab.Logger
          .newBuilder()
          .setLoggerName(loggerNamesAndCounts.loggerName);
        for (Map.Entry<String, Integer> levelCountEntry : loggerNamesAndCounts.levelCounts.entrySet()) {
          long count = levelCountEntry.getValue();
          switch (levelCountEntry.getKey()) {
            case "debugs":
              builder.setDebugs(count);
              break;
            case "infos":
              builder.setInfos(count);
              break;
            case "errors":
              builder.setErrors(count);
              break;
            case "warns":
              builder.setWarns(count);
              break;
            default:
              throw new IllegalArgumentException(
                String.format("unexpected level %s", levelCountEntry.getKey())
              );
          }
        }
        return builder.build();
      })
      .collect(Collectors.toList());

    TelemetryAccumulator telemetryAccumulator = getTelemetryAccumulator(
      prefabCloudClient
    );

    await()
      .atMost(Duration.of(30, ChronoUnit.SECONDS))
      .untilAsserted(() -> {
        List<Prefab.LoggersTelemetryEvent> loggerEvents = telemetryAccumulator
          .getTelemetryEventsList()
          .stream()
          .map(Prefab.TelemetryEvents::getEventsList)
          .flatMap(List::stream)
          .filter(Prefab.TelemetryEvent::hasLoggers)
          .map(Prefab.TelemetryEvent::getLoggers)
          .collect(Collectors.toList());
        List<Prefab.Logger> loggers = loggerEvents
          .stream()
          .map(Prefab.LoggersTelemetryEvent::getLoggersList)
          .flatMap(List::stream)
          .collect(Collectors.toList());
        for (Prefab.LoggersTelemetryEvent loggerEvent : loggerEvents) {
          long thirtySecondsAgo = Instant
            .now()
            .minus(30, ChronoUnit.SECONDS)
            .toEpochMilli();
          assertThat(loggerEvent.getStartAt())
            .as("start time should be recent")
            .isGreaterThan(thirtySecondsAgo);
          assertThat(loggerEvent.getEndAt())
            .as("end time should be greater than start time")
            .isGreaterThan(loggerEvent.getStartAt());
        }
        assertThat(loggers).containsExactlyInAnyOrderElementsOf(expectedLoggers);
      });
  }

  public static class LoggerNamesAndCounts {

    private final String loggerName;
    private final Map<String, Integer> levelCounts;

    @JsonCreator
    LoggerNamesAndCounts(
      @JsonProperty("logger_name") String loggerName,
      @JsonProperty("counts") Map<String, Integer> levelCounts
    ) {
      this.loggerName = loggerName;
      this.levelCounts = levelCounts;
    }
  }
}
