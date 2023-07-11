package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.client.config.Match;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.domain.Prefab;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.failsafe.Bulkhead;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(MatchProcessor.class);

  private final Options options;
  private final PrefabHttpClient prefabHttpClient;
  private final Clock clock;

  private static final int DRAIN_SIZE = 25_000;
  private static final int QUEUE_SIZE = 1_000_000_000;

  private static final int OUTPUT_QUEUE_SIZE = 10;
  private final List<Event> drain = new ArrayList<>(DRAIN_SIZE); // don't allocate a new one every run

  private final LinkedBlockingQueue<OutputBuffer> outputQueue = new LinkedBlockingQueue<>(
    OUTPUT_QUEUE_SIZE
  );

  private final LinkedBlockingQueue<Event> matchQueue = new LinkedBlockingQueue<>(
    QUEUE_SIZE
  );

  private final MatchStatsAggregator matchStatsAggregator = new MatchStatsAggregator();
  private final ContextDeduplicator contextDeduplicator;

  MatchProcessor(Options options, PrefabHttpClient prefabHttpClient, Clock clock) {
    this.options = options;
    this.prefabHttpClient = prefabHttpClient;
    this.clock = clock;
    this.contextDeduplicator = new ContextDeduplicator();
  }

  void start() {
    ThreadFactory aggregatorFactory = new ThreadFactoryBuilder()
      .setDaemon(true)
      .setNameFormat("prefab-match-processor-aggregator-%d")
      .build();

    Thread aggregatorThread = aggregatorFactory.newThread(this::aggregationLoop);
    aggregatorThread.setDaemon(true);
    aggregatorThread.setUncaughtExceptionHandler((t, e) ->
      LOG.error("uncaught exception in thread t {}", t.getName(), e)
    );
    aggregatorThread.start();

    ThreadFactory uploaderFactory = new ThreadFactoryBuilder()
      .setDaemon(true)
      .setNameFormat("prefab-match-processor-uploader-%d")
      .build();

    Thread uploaderThread = uploaderFactory.newThread(this::uploadLoop);
    uploaderThread.setUncaughtExceptionHandler((t, e) ->
      LOG.error("uncaught exception in thread t {}", t.getName(), e)
    );
    uploaderThread.setDaemon(true);
    uploaderThread.start();

    ScheduledExecutorService executorService = MoreExecutors.getExitingScheduledExecutorService(
      new ScheduledThreadPoolExecutor(
        1,
        r -> new Thread(r, "prefab-match-processor-flusher")
      ),
      100,
      TimeUnit.MILLISECONDS
    );

    executorService.scheduleAtFixedRate(this::flushStats, 10, 10, TimeUnit.SECONDS);
  }

  void reportMatch(Match match, LookupContext lookupContext) {
    if (
      match.getConfigElement().getConfig().getConfigType() ==
      Prefab.ConfigType.FEATURE_FLAG
    ) {
      matchQueue.offer(new MatchEvent(clock.millis(), match, lookupContext));
    }
  }

  void aggregationLoop() {
    Set<Prefab.ExampleContext> recentlySeenContexts = new HashSet<>(); //make bounded!
    while (true) {
      try {
        drain.add(matchQueue.take()); //blocks
        matchQueue.drainTo(drain, DRAIN_SIZE - 1);
        for (Event event : drain) {
          if (event.eventType == Event.EventType.MATCH) {
            MatchEvent matchEvent = (MatchEvent) event;
            matchStatsAggregator.recordMatch(matchEvent.match, event.timestamp);
            PrefabContextSet context = matchEvent.lookupContext.getPrefabContextSet();
            String fingerPrint = context.getFingerPrint();
            if (!fingerPrint.isBlank()) {
              if (!contextDeduplicator.recentlySeen(fingerPrint)) {
                recentlySeenContexts.add(
                  Prefab.ExampleContext
                    .newBuilder()
                    .setTimestamp(event.timestamp)
                    .setContextSet(PrefabContextSet.convert(context).toProto())
                    .build()
                );
              }
            }
          }
          if (event.eventType == Event.EventType.FLUSH) {
            MatchStatsAggregator.StatsAggregate stats = matchStatsAggregator.getAndResetStatsAggregate();
            Set<Prefab.ExampleContext> contextsToSend = recentlySeenContexts;
            recentlySeenContexts = new HashSet<>();
            if (!outputQueue.offer(new OutputBuffer(contextsToSend, stats))) {
              //restore state and keep aggregating
              matchStatsAggregator.setStatsAggregate(stats);
              recentlySeenContexts = contextsToSend;
            }
          }
        }
        drain.clear();
      } catch (InterruptedException e) {
        //IGNORE
      }
    }
  }

  private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(429, 500); //TODO add more

  void uploadLoop() {
    Bulkhead<HttpResponse<Supplier<Prefab.TelemetryEventsResponse>>> bulkhead = Bulkhead
      .<HttpResponse<Supplier<Prefab.TelemetryEventsResponse>>>builder(5)
      .build();

    RetryPolicy<HttpResponse<Supplier<Prefab.TelemetryEventsResponse>>> retryPolicy = RetryPolicy
      .<HttpResponse<Supplier<Prefab.TelemetryEventsResponse>>>builder()
      .withMaxRetries(3)
      .withBackoff(1, 10, ChronoUnit.SECONDS)
      .handleResultIf(r -> RETRYABLE_STATUS_CODES.contains(r.statusCode()))
      .build();

    while (true) {
      try {
        bulkhead.acquirePermit();
        OutputBuffer outputBuffer = outputQueue.take();
        Prefab.TelemetryEvents telemetryEvents = outputBuffer.toTelemetryEvents();
        if (!telemetryEvents.getEventsList().isEmpty()) {
          LOG.debug("Uploading {}", telemetryEvents);
          Failsafe
            .with(retryPolicy)
            .getStageAsync(() -> prefabHttpClient.reportTelemetryEvents(telemetryEvents))
            .whenComplete((r, t) -> {
              // don't care if error or not here, just want to release the permit
              bulkhead.releasePermit();
            });
        } else {
          bulkhead.releasePermit();
        }
      } catch (InterruptedException e) {
        // continue
      }
    }
  }

  void flushStats() {
    matchQueue.offer(new MatchProcessor.Event(Event.EventType.FLUSH, clock.millis()));
  }

  private static class Event {

    enum EventType {
      MATCH,
      FLUSH,
    }

    EventType eventType;
    long timestamp;

    Event(EventType eventType, long timestamp) {
      this.eventType = eventType;
      this.timestamp = timestamp;
    }
  }

  private static class MatchEvent extends Event {

    Match match;
    LookupContext lookupContext;

    MatchEvent(long timestamp, Match match, LookupContext lookupContext) {
      super(EventType.MATCH, timestamp);
      this.match = match;
      this.lookupContext = lookupContext;
    }
  }

  private static class ContextDeduplicator {

    private final Cache<String, String> cache;

    ContextDeduplicator() {
      this.cache =
        CacheBuilder
          .newBuilder()
          .expireAfterWrite(10, TimeUnit.MINUTES)
          .maximumSize(1000)
          .build();
    }

    boolean recentlySeen(String fingerprint) {
      if (cache.getIfPresent(fingerprint) != null) {
        return true;
      }
      cache.put(fingerprint, fingerprint);
      return false;
    }
  }

  private static class OutputBuffer {

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
