package cloud.prefab.client.integration.telemetry;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Map.entry;
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

        Map<String, Prefab.ConfigEvaluationSummary> mergedActualSummariesByKey = actualSummaries
          .stream()
          .collect(Collectors.groupingBy(Prefab.ConfigEvaluationSummary::getKey))
          .entrySet()
          .stream()
          .map(entry -> entry(entry.getKey(), merge(entry.getValue())))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(mergedActualSummariesByKey.keySet())
          .containsAll(Set.copyOf(keysToEvaluate));

        expectedDataByKey
          .entrySet()
          .forEach(entry -> {
            String key = entry.getKey();
            ExpectedDatum expectedDatum = entry.getValue();
            Prefab.ConfigEvaluationSummary actualSummary = mergedActualSummariesByKey.get(
              key
            );
            assertThat(actualSummary.getCountersCount()).isEqualTo(1);
            Prefab.ConfigEvaluationCounter onlyCount = actualSummary
              .getCountersList()
              .get(0);
            assertThat(expectedDatum.configType).isEqualTo(actualSummary.getType());
            assertThat(expectedDatum.count).isEqualTo(onlyCount.getCount());
            assertThat(expectedDatum.configValue).isEqualTo(onlyCount.getSelectedValue());
            assertThat(expectedDatum.summaryNode.conditionalValueIndex)
              .isEqualTo(onlyCount.getConditionalValueIndex());
            assertThat(expectedDatum.summaryNode.configRowIndex)
              .isEqualTo(onlyCount.getConfigRowIndex());
            assertThat(expectedDatum.summaryNode.weightedValueIndex)
              .isEqualTo(
                onlyCount.hasWeightedValueIndex()
                  ? Optional.of(onlyCount.getWeightedValueIndex())
                  : Optional.empty()
              );
          });
      });
  }

  Prefab.ConfigEvaluationSummary merge(List<Prefab.ConfigEvaluationSummary> summaryList) {
    if (summaryList.size() == 1) {
      return summaryList.get(0);
    }
    Map<Prefab.ConfigEvaluationCounter, Optional<Prefab.ConfigEvaluationCounter>> foo = summaryList
      .stream()
      .flatMap(summary -> summary.getCountersList().stream())
      .collect(
        Collectors.groupingBy(
          c -> c.toBuilder().setCount(0).build(),
          Collectors.reducing((configEvaluationCounter, configEvaluationCounter2) ->
            configEvaluationCounter
              .toBuilder()
              .setCount(
                configEvaluationCounter.getCount() + configEvaluationCounter2.getCount()
              )
              .build()
          )
        )
      );
    return summaryList
      .get(0)
      .toBuilder()
      .clearCounters()
      .addAllCounters(
        foo.values().stream().flatMap(Optional::stream).collect(Collectors.toList())
      )
      .build();
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

    private final Summary summaryNode;

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
      this.summaryNode = summaryNode;
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
