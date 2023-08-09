package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.domain.Prefab;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchStatsAggregatorTest {

  static final Prefab.Config TF_CONFIG_1 = Prefab.Config
    .newBuilder()
    .setConfigType(Prefab.ConfigType.FEATURE_FLAG)
    .setKey("the.key")
    .setId(1L)
    .addAllowableValues(ConfigValueUtils.from(true))
    .addAllowableValues(ConfigValueUtils.from(false))
    .addRows(
      Prefab.ConfigRow
        .newBuilder()
        .addValues(
          Prefab.ConditionalValue
            .newBuilder()
            .setValue(ConfigValueUtils.from(true))
            .build()
        )
    )
    .build();

  static final Prefab.Config TF_CONFIG_2 = TF_CONFIG_1.toBuilder().setId(2L).build();

  static final Prefab.Config ONE_TWO_CONFIG_2 = Prefab.Config
    .newBuilder()
    .setConfigType(Prefab.ConfigType.FEATURE_FLAG)
    .setKey("another.key")
    .setId(1L)
    .addAllowableValues(ConfigValueUtils.from(1))
    .addAllowableValues(ConfigValueUtils.from(2))
    .addRows(
      Prefab.ConfigRow
        .newBuilder()
        .addValues(
          Prefab.ConditionalValue.newBuilder().setValue(ConfigValueUtils.from(1)).build()
        )
    )
    .build();

  MatchStatsAggregator matchStatsAggregator;

  @BeforeEach
  void beforeEach() {
    matchStatsAggregator = new MatchStatsAggregator();
  }

  @Test
  void itAggregates() {
    matchStatsAggregator.recordMatch(
      new Match(
        ConfigValueUtils.from(true),
        new ConfigElement(TF_CONFIG_1, new Provenance(ConfigClient.Source.STREAMING)),
        Collections.emptyList(),
        0,
        2,
        Optional.empty()
      ),
      101
    );

    matchStatsAggregator.recordMatch(
      new Match(
        ConfigValueUtils.from(false),
        new ConfigElement(TF_CONFIG_1, new Provenance(ConfigClient.Source.STREAMING)),
        Collections.emptyList(),
        0,
        2,
        Optional.empty()
      ),
      102
    );

    matchStatsAggregator.recordMatch(
      new Match(
        ConfigValueUtils.from(false),
        new ConfigElement(TF_CONFIG_2, new Provenance(ConfigClient.Source.STREAMING)),
        Collections.emptyList(),
        0,
        2,
        Optional.empty()
      ),
      102
    );

    matchStatsAggregator.recordMatch(
      new Match(
        ConfigValueUtils.from(1),
        new ConfigElement(
          ONE_TWO_CONFIG_2,
          new Provenance(ConfigClient.Source.STREAMING)
        ),
        Collections.emptyList(),
        0,
        3,
        Optional.empty()
      ),
      107
    );

    MatchStatsAggregator.StatsAggregate statsAggregate = matchStatsAggregator.getStatsAggregate();

    assertThat(statsAggregate.getMinTime()).isEqualTo(101);
    assertThat(statsAggregate.getMaxTime()).isEqualTo(107);
    assertThat(statsAggregate.getCounterData())
      .isEqualTo(
        Map.of(
          new MatchStatsAggregator.ConfigKeyAndTypeKey(
            "another.key",
            Prefab.ConfigType.FEATURE_FLAG
          ),
          Map.of(
            new MatchStatsAggregator.CountKey(
              1,
              ConfigValueUtils.from(1),
              0,
              0,
              3,
              Optional.empty()
            ),
            new MatchStatsAggregator.Counter(1)
          ),
          new MatchStatsAggregator.ConfigKeyAndTypeKey(
            "the.key",
            Prefab.ConfigType.FEATURE_FLAG
          ),
          Map.of(
            new MatchStatsAggregator.CountKey(
              1,
              ConfigValueUtils.from(true),
              0,
              0,
              2,
              Optional.empty()
            ),
            new MatchStatsAggregator.Counter(1),
            new MatchStatsAggregator.CountKey(
              1,
              ConfigValueUtils.from(false),
              1,
              0,
              2,
              Optional.empty()
            ),
            new MatchStatsAggregator.Counter(1),
            new MatchStatsAggregator.CountKey(
              2,
              ConfigValueUtils.from(false),
              1,
              0,
              2,
              Optional.empty()
            ),
            new MatchStatsAggregator.Counter(1)
          )
        )
      );
    /*

    FIXME: ignoreCollectionOrder isn't working properly here so we need to (at least for testing) impose a canonical ordering on both for comparison
    assertThat(statsAggregate.toProto())
      .usingRecursiveComparison(
        RecursiveComparisonConfiguration.builder().withIgnoreCollectionOrder(true).build()
      )
      .isEqualTo(
        Prefab.ConfigEvaluationSummaries
          .newBuilder()
          .setStart(101)
          .setEnd(107)
          .addSummaries(
            Prefab.ConfigEvaluationSummary
              .newBuilder()
              .setKey("the.key")
              .setType(Prefab.ConfigType.FEATURE_FLAG)
              .addCounters(
                Prefab.ConfigEvaluationCounter
                  .newBuilder()
                  .setConfigId(1)
                  .setSelectedValue(ConfigValueUtils.from(false))
                  .setSelectedIndex(1)
                  .setCount(1)
                  .build()
              )
              .addCounters(
                Prefab.ConfigEvaluationCounter
                  .newBuilder()
                  .setConfigId(2)
                  .setSelectedValue(ConfigValueUtils.from(false))
                  .setSelectedIndex(1)
                  .setCount(1)
                  .build()
              )
              .addCounters(
                Prefab.ConfigEvaluationCounter
                  .newBuilder()
                  .setConfigId(1)
                  .setSelectedValue(ConfigValueUtils.from(true))
                  .setSelectedIndex(0)
                  .setCount(1)
                  .build()
              )
              .build()
          )
          .addSummaries(
            Prefab.ConfigEvaluationSummary
              .newBuilder()
              .setKey("another.key")
              .setType(Prefab.ConfigType.FEATURE_FLAG)
              .addCounters(
                Prefab.ConfigEvaluationCounter
                  .newBuilder()
                  .setConfigId(1)
                  .setSelectedValue(ConfigValueUtils.from(1))
                  .setSelectedIndex(0)
                  .setCount(1)
                  .build()
              )
              .build()
          )
          .build() );


     */

  }
}
