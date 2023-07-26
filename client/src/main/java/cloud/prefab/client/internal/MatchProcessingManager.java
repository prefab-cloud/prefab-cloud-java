package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.client.config.Match;
import cloud.prefab.domain.Prefab;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchProcessingManager {

  private static final int OUTPUT_QUEUE_SIZE = 10;

  private final MatchProcessor matchProcessor;
  private final TelemetryUploader uploader;

  MatchProcessingManager(PrefabHttpClient prefabHttpClient, Options options) {
    LinkedBlockingQueue<OutputBuffer> outputQueue = new LinkedBlockingQueue<>(
      OUTPUT_QUEUE_SIZE
    );
    matchProcessor = new MatchProcessor(outputQueue, Clock.systemUTC(), options);
    uploader = new TelemetryUploader(outputQueue, prefabHttpClient, options);
  }

  public void start() {
    uploader.start();
    matchProcessor.start();
  }

  public void reportMatch(Match match, LookupContext lookupContext) {
    matchProcessor.reportMatch(match, lookupContext);
  }

  static class OutputBuffer {

    static final Logger LOG = LoggerFactory.getLogger(OutputBuffer.class);

    Set<Prefab.ExampleContext> recentlySeenContexts;
    MatchStatsAggregator.StatsAggregate statsAggregate;

    public OutputBuffer(
      Set<Prefab.ExampleContext> recentlySeenContexts,
      MatchStatsAggregator.StatsAggregate statsAggregate
    ) {
      this.recentlySeenContexts = recentlySeenContexts;
      this.statsAggregate = statsAggregate;
    }

    Prefab.TelemetryEvents toTelemetryEvents() {
      Prefab.TelemetryEvents.Builder telemetryEventsBuilder = Prefab.TelemetryEvents.newBuilder();
      if (!recentlySeenContexts.isEmpty()) {
        LOG.debug(
          "adding {} recently seen contexts to telemetry bundle",
          recentlySeenContexts.size()
        );
        telemetryEventsBuilder.addEvents(
          Prefab.TelemetryEvent
            .newBuilder()
            .setExampleContexts(
              Prefab.ExampleContexts
                .newBuilder()
                .addAllExamples(recentlySeenContexts)
                .build()
            )
            .build()
        );
      } else {
        LOG.debug("No recently seen contexts for telemetry bundle");
      }
      if (!statsAggregate.getCounterData().isEmpty()) {
        telemetryEventsBuilder.addEvents(
          Prefab.TelemetryEvent.newBuilder().setSummaries(statsAggregate.toProto())
        );
      }

      return telemetryEventsBuilder.build();
    }
  }
}
