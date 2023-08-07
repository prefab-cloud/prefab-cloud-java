package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.client.config.Match;
import cloud.prefab.domain.Prefab;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    ScheduledExecutorService executorService = MoreExecutors.getExitingScheduledExecutorService(
      new ScheduledThreadPoolExecutor(
        1,
        r -> new Thread(r, "prefab-match-processor-flusher")
      ),
      100,
      TimeUnit.MILLISECONDS
    );

    executorService.scheduleAtFixedRate(
      matchProcessor::flushStats,
      10,
      10,
      TimeUnit.SECONDS
    );
  }

  public void reportMatch(Match match, LookupContext lookupContext) {
    matchProcessor.reportMatch(match, lookupContext);
  }

  static class OutputBuffer {

    static final Logger LOG = LoggerFactory.getLogger(OutputBuffer.class);
    private final long droppedEventCount;

    private final long startTime;
    private final long endTime;
    Set<Prefab.ExampleContext> recentlySeenContexts;
    MatchStatsAggregator.StatsAggregate statsAggregate;

    public OutputBuffer(
            long startTime, long endTime,
      Set<Prefab.ExampleContext> recentlySeenContexts,
      MatchStatsAggregator.StatsAggregate statsAggregate,
      long droppedEventCount
    ) {
      this.startTime = startTime;
      this.endTime = endTime;
      this.recentlySeenContexts = recentlySeenContexts;
      this.statsAggregate = statsAggregate;
      this.droppedEventCount = droppedEventCount;
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
      if (droppedEventCount > 0) {
        telemetryEventsBuilder.addEvents(Prefab.TelemetryEvent.newBuilder().setClientStats(Prefab.ClientStats.newBuilder().setDroppedEventCount(droppedEventCount).setStart(startTime).setEnd(endTime).build()));
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
