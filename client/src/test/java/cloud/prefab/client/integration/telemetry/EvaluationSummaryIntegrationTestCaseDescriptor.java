package cloud.prefab.client.integration.telemetry;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.integration.IntegrationTestClientOverrides;
import cloud.prefab.client.integration.IntegrationTestFunction;
import cloud.prefab.domain.Prefab;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluationSummaryIntegrationTestCaseDescriptor
  extends TelemetryIntegrationTestCaseDescriptor {

  private static final Logger LOG = LoggerFactory.getLogger(
    EvaluationSummaryIntegrationTestCaseDescriptor.class
  );
  private final List<String> keysToEvaluate;
  private final List<ExpectedDatum> expectedData;

  @JsonCreator
  public EvaluationSummaryIntegrationTestCaseDescriptor(
    @JsonProperty("name") String name,
    @JsonProperty("client") String client,
    @JsonProperty("client_overrides") IntegrationTestClientOverrides clientOverrides,
    @JsonProperty("endpoint") String endpoint,
    @JsonProperty("function") IntegrationTestFunction function,
    @JsonProperty("aggregator") String aggregator,
    @JsonProperty("data") List<String> keysToEvaluate,
    @JsonProperty("expected_data") List<ExpectedDatum> expectedData
  ) {
    super(
      name,
      MoreObjects.firstNonNull(clientOverrides, IntegrationTestClientOverrides.empty())
    );
    this.keysToEvaluate = keysToEvaluate;
    this.expectedData = expectedData;
    LOG.info("expected data is {}", expectedData);
  }

  @Override
  protected void performVerification(PrefabCloudClient prefabCloudClient) {
    LOG.info("performVerification");
    ConfigClient configClient = prefabCloudClient.configClient();

    for (String key : keysToEvaluate) {
      configClient.get(key);
    }

    Map<String, ExpectedDatum> expectedDataByKey = expectedData
      .stream()
      .collect(toImmutableMap(ExpectedDatum::getKey, Function.identity()));

    await()
      .atMost(Duration.of(3, ChronoUnit.SECONDS))
      .untilAsserted(() -> {
        List<Prefab.ConfigEvaluationSummary> actualSummaries = getTelemetryAccumulator(
          prefabCloudClient
        )
          .getTelemetryEventsList()
          .stream()
          .flatMap(t -> t.getEventsList().stream())
          .filter(Prefab.TelemetryEvent::hasSummaries)
          .flatMap(s -> s.getSummaries().getSummariesList().stream())
          .collect(Collectors.toList());

        Map<String, List<Prefab.ConfigEvaluationSummary>> actualSummariesByKey = actualSummaries
          .stream()
          .collect(Collectors.groupingBy(Prefab.ConfigEvaluationSummary::getKey));
        assertThat(actualSummariesByKey.keySet()).containsAll(Set.copyOf(keysToEvaluate));
        // may need to sum the actual values because telemetry could be split over more than one upload
        expectedDataByKey
          .entrySet()
          .forEach(entry -> {
            String key = entry.getKey();
            ExpectedDatum expectedDatum = entry.getValue();
            List<Prefab.ConfigEvaluationSummary> summaries = actualSummariesByKey.get(
              key
            );
            long expectedTotalCount = summaries
              .stream()
              .flatMap(s -> s.getCountersList().stream())
              .mapToLong(Prefab.ConfigEvaluationCounter::getCount)
              .sum();
            assertThat(expectedDatum.count).isEqualTo(expectedTotalCount);
          });
        //TODO compare the value types etc
      });
  }

  static class ExpectedDatum {

    static class Summary {

      private final int configRowIndex;
      private final int conditionalValueIndex;
      private final Optional<Integer> weightedValueIndex;

      @JsonCreator
      Summary(
        @JsonProperty("config_row_index") int configRowIndex,
        @JsonProperty("conditional_value_index") int conditionalValueIndex,
        @JsonProperty("weighted_value_index") Optional<Integer> weightedValueIndex
      ) {
        this.configRowIndex = configRowIndex;
        this.conditionalValueIndex = conditionalValueIndex;
        this.weightedValueIndex = weightedValueIndex;
      }
    }

    private final String key;
    private final String valueType;
    private final int count;

    final Prefab.ConfigValue configValue;

    public Prefab.ConfigType getConfigType() {
      return configType;
    }

    private final Prefab.ConfigType configType;

    public String getKey() {
      return key;
    }

    public String getValueType() {
      return valueType;
    }

    public int getCount() {
      return count;
    }

    @JsonIgnore // if not ignored, yaml deserializer gets confused even though there's a JsonCreator annotated method
    public Prefab.ConfigValue getConfigValue() {
      return configValue;
    }

    @JsonCreator
    ExpectedDatum(
      @JsonProperty("key") String key,
      @JsonProperty("type") String configType,
      @JsonProperty("value_type") String valueType,
      @JsonProperty("count") int count,
      @JsonProperty("value") JsonNode valueNode,
      @JsonProperty("summary") Summary summaryNode
    ) {
      LOG.info("parsing expected datum {}", key);
      this.key = key;
      this.configType = Prefab.ConfigType.valueOf(configType);
      this.valueType = valueType;
      this.count = count;

      switch (valueType) {
        case "string":
          configValue = ConfigValueUtils.from(valueNode.asText());
          break;
        case "string_list":
          if (valueNode.isArray()) {
            List<String> stringList = new ArrayList<>();
            for (JsonNode arrayItem : valueNode) {
              stringList.add(arrayItem.asText());
            }
            configValue = ConfigValueUtils.from(stringList);
          } else {
            throw new RuntimeException("expecting value node to be an array");
          }
          break;
        case "int":
          configValue = ConfigValueUtils.from(valueNode.asLong());
          break;
        default:
          throw new RuntimeException(
            String.format("non supported value type %s", valueType)
          );
      }
    }
  }
}
