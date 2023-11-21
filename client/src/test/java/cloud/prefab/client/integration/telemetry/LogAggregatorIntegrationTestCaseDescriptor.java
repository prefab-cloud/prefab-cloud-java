package cloud.prefab.client.integration.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.in;
import static org.awaitility.Awaitility.await;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.integration.IntegrationTestClientOverrides;
import cloud.prefab.client.integration.IntegrationTestFunction;
import cloud.prefab.client.integration.TelemetryAccumulator;
import cloud.prefab.domain.Prefab;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
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
  private final Map<String, List<Integer>> data;

  private final List<Prefab.LogLevel> logLevelList = List.of(
    Prefab.LogLevel.DEBUG,
    Prefab.LogLevel.INFO,
    Prefab.LogLevel.WARN,
    Prefab.LogLevel.ERROR
  );
  private final List<ExpectedDatum> expectedData;

  @JsonCreator
  public LogAggregatorIntegrationTestCaseDescriptor(
    @JsonProperty("name") String name,
    @JsonProperty("client") String client,
    @JsonProperty("client_overrides") IntegrationTestClientOverrides clientOverrides,
    @JsonProperty("endpoint") String endpoint,
    @JsonProperty("function") IntegrationTestFunction function,
    @JsonProperty("aggregator") String aggregator,
    @JsonProperty("data") Map<String, List<Integer>> data,
    @JsonProperty("expected_data") List<ExpectedDatum> expectedData
  ) {
    super(
      name,
      MoreObjects.firstNonNull(clientOverrides, IntegrationTestClientOverrides.empty())
    );
    this.data = data;
    this.expectedData = expectedData;
  }

  @Override
  protected void performVerification(PrefabCloudClient prefabCloudClient) {
    //TODO update the data format so data and expected data are the same
    for (Map.Entry<String, List<Integer>> stringListEntry : data.entrySet()) {
      String loggerName = stringListEntry.getKey();
      int index = 0;
      for (Integer logCount : stringListEntry.getValue()) {
        if (logCount > 0) {
          Prefab.LogLevel logLevel = logLevelList.get(index);
          prefabCloudClient
            .configClient()
            .reportLoggerUsage(loggerName, logLevel, logCount);
        }
        index++;
      }
    }

    TelemetryAccumulator telemetryAccumulator = getTelemetryAccumulator(
      prefabCloudClient
    );

    await()
      .atMost(Duration.of(30, ChronoUnit.SECONDS))
      .untilAsserted(() -> {
        List<Prefab.Logger> loggers = telemetryAccumulator
          .getTelemetryEventsList()
          .stream()
          .map(Prefab.TelemetryEvents::getEventsList)
          .flatMap(List::stream)
          .filter(Prefab.TelemetryEvent::hasLoggers)
          .map(Prefab.TelemetryEvent::getLoggers)
          .map(Prefab.LoggersTelemetryEvent::getLoggersList)
          .flatMap(List::stream)
          .collect(Collectors.toList());
        assertThat(loggers).hasSize(2);
      });
  }

  public static class ExpectedDatum {

    private final String loggerName;
    private final Map<String, Integer> levelCounts;

    @JsonCreator
    ExpectedDatum(
      @JsonProperty("logger_name") String loggerName,
      @JsonProperty("counts") Map<String, Integer> levelCounts
    ) {
      this.loggerName = loggerName;
      this.levelCounts = levelCounts;
    }
  }
}
