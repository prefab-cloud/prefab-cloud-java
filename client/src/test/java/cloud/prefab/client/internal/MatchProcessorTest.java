package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.domain.Prefab;
import java.time.Clock;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchProcessorTest {

  static final Prefab.Config TF_CONFIG = Prefab.Config
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

  @Test
  void itRecordsCountsAndContexts() throws InterruptedException {
    LinkedBlockingQueue<MatchProcessingManager.OutputBuffer> outputQueue = new LinkedBlockingQueue<>();
    MatchProcessor matchProcessor = new MatchProcessor(
      outputQueue,
      Clock.systemUTC(),
      new Options().setContextUploadMode(Options.CollectContextMode.PERIODIC_EXAMPLE).setCollectEvaluationSummaries(true)
    );
    matchProcessor.start();

    reportSomeMatches(matchProcessor);
    matchProcessor.flushStats();
    MatchProcessingManager.OutputBuffer item = outputQueue.poll(10, TimeUnit.SECONDS);
    assertThat(item).isNotNull();

    assertThat(item.recentlySeenContexts).hasSize(2);
    assertThat(item.statsAggregate.getCounterData())
      .containsKey(
        new MatchStatsAggregator.ConfigKeyAndTypeKey(
          TF_CONFIG.getKey(),
          TF_CONFIG.getConfigType()
        )
      );

    matchProcessor.flushStats();
    MatchProcessingManager.OutputBuffer nextItem = outputQueue.poll(10, TimeUnit.SECONDS);
    assertThat(nextItem).isNotNull();
    assertThat(nextItem.recentlySeenContexts).isEmpty();
    assertThat(nextItem.statsAggregate.getCounterData()).isEmpty();
  }


  @Test
  void itRecordsCountsWithoutContexts() throws InterruptedException {
    LinkedBlockingQueue<MatchProcessingManager.OutputBuffer> outputQueue = new LinkedBlockingQueue<>();
    MatchProcessor matchProcessor = new MatchProcessor(
            outputQueue,
            Clock.systemUTC(),
            new Options().setContextUploadMode(Options.CollectContextMode.SHAPE_ONLY).setCollectEvaluationSummaries(true)
    );
    matchProcessor.start();

    reportSomeMatches(matchProcessor);
    matchProcessor.flushStats();
    MatchProcessingManager.OutputBuffer item = outputQueue.poll(10, TimeUnit.SECONDS);
    assertThat(item).isNotNull();

    assertThat(item.recentlySeenContexts).isEmpty();
    assertThat(item.statsAggregate.getCounterData())
            .containsKey(
                    new MatchStatsAggregator.ConfigKeyAndTypeKey(
                            TF_CONFIG.getKey(),
                            TF_CONFIG.getConfigType()
                    )
            );

    matchProcessor.flushStats();
    MatchProcessingManager.OutputBuffer nextItem = outputQueue.poll(10, TimeUnit.SECONDS);
    assertThat(nextItem).isNotNull();
    assertThat(nextItem.recentlySeenContexts).isEmpty();
    assertThat(nextItem.statsAggregate.getCounterData()).isEmpty();
  }

  @Test
  void itRecordsContextsWithoutCounts() throws InterruptedException {
    LinkedBlockingQueue<MatchProcessingManager.OutputBuffer> outputQueue = new LinkedBlockingQueue<>();
    MatchProcessor matchProcessor = new MatchProcessor(
            outputQueue,
            Clock.systemUTC(),
            new Options().setContextUploadMode(Options.CollectContextMode.PERIODIC_EXAMPLE).setCollectEvaluationSummaries(false)
    );
    matchProcessor.start();

    reportSomeMatches(matchProcessor);
    matchProcessor.flushStats();
    MatchProcessingManager.OutputBuffer item = outputQueue.poll(10, TimeUnit.SECONDS);
    assertThat(item).isNotNull();

    assertThat(item.recentlySeenContexts).hasSize(2);
    assertThat(item.statsAggregate.getCounterData()).isEmpty();

    matchProcessor.flushStats();
    MatchProcessingManager.OutputBuffer nextItem = outputQueue.poll(10, TimeUnit.SECONDS);
    assertThat(nextItem).isNotNull();
    assertThat(nextItem.recentlySeenContexts).isEmpty();
    assertThat(nextItem.statsAggregate.getCounterData()).isEmpty();
  }

  @Test
  void itRecordsNothing() throws InterruptedException {
    LinkedBlockingQueue<MatchProcessingManager.OutputBuffer> outputQueue = new LinkedBlockingQueue<>();
    MatchProcessor matchProcessor = new MatchProcessor(
            outputQueue,
            Clock.systemUTC(),
            new Options().setContextUploadMode(Options.CollectContextMode.SHAPE_ONLY).setCollectEvaluationSummaries(false)
    );
    matchProcessor.start();

    reportSomeMatches(matchProcessor);
    matchProcessor.flushStats();
    MatchProcessingManager.OutputBuffer item = outputQueue.poll(10, TimeUnit.SECONDS);
    assertThat(item).isNotNull();

    assertThat(item.recentlySeenContexts).isEmpty();
    assertThat(item.statsAggregate.getCounterData()).isEmpty();

    matchProcessor.flushStats();
    MatchProcessingManager.OutputBuffer nextItem = outputQueue.poll(10, TimeUnit.SECONDS);
    assertThat(nextItem).isNotNull();
    assertThat(nextItem.recentlySeenContexts).isEmpty();
    assertThat(nextItem.statsAggregate.getCounterData()).isEmpty();
  }

  private static void reportSomeMatches(MatchProcessor matchProcessor) {
    matchProcessor.reportMatch(
      new Match(
        ConfigValueUtils.from(false),
        new ConfigElement(TF_CONFIG, new Provenance(ConfigClient.Source.STREAMING)),
        Collections.emptyList(),
        1,
        2,
        Optional.empty()
      ),
      new LookupContext(
        Optional.empty(),
        PrefabContextSet.from(
          PrefabContext.newBuilder("user").put("key", "u123").build(),
          PrefabContext.newBuilder("team").put("key", "t123").build()
        )
      )
    );

    matchProcessor.reportMatch(
      new Match(
        ConfigValueUtils.from(true),
        new ConfigElement(TF_CONFIG, new Provenance(ConfigClient.Source.STREAMING)),
        Collections.emptyList(),
        1,
        2,
        Optional.empty()
      ),
      new LookupContext(
        Optional.empty(),
        PrefabContextSet.from(
          PrefabContext.newBuilder("user").put("key", "u123").build(),
          PrefabContext.newBuilder("team").put("key", "t123").build()
        )
      )
    );

    matchProcessor.reportMatch(
      new Match(
        ConfigValueUtils.from(true),
        new ConfigElement(TF_CONFIG, new Provenance(ConfigClient.Source.STREAMING)),
        Collections.emptyList(),
        1,
        2,
        Optional.empty()
      ),
      new LookupContext(
        Optional.empty(),
        PrefabContextSet.from(
          PrefabContext.newBuilder("user").put("key", "u124").build(),
          PrefabContext.newBuilder("team").put("key", "t123").build()
        )
      )
    );
  }
  //TODO write a test where the output queue is full and processor continues to accumulate to the same buffer
}
