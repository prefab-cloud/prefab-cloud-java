package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.client.config.Match;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.domain.Prefab;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(MatchProcessor.class);

  private final Options options;
  private final Clock clock;

  private static final int DRAIN_SIZE = 25_000;
  private static final int QUEUE_SIZE = 1_000_000_000;

  private final List<Event> drain = new ArrayList<>(DRAIN_SIZE); // don't allocate a new one every run

  private final LinkedBlockingQueue<MatchProcessingManager.OutputBuffer> outputQueue;

  private final LinkedBlockingQueue<Event> matchQueue = new LinkedBlockingQueue<>(
    QUEUE_SIZE
  );

  private final MatchStatsAggregator matchStatsAggregator = new MatchStatsAggregator();
  private final ContextDeduplicator contextDeduplicator;

  MatchProcessor(
    LinkedBlockingQueue<MatchProcessingManager.OutputBuffer> outputQueue,
    Clock clock,
    Options options
  ) {
    this.options = options;
    this.clock = clock;
    this.contextDeduplicator = new ContextDeduplicator(Duration.ofMinutes(15), 1000);
    this.outputQueue = outputQueue;
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

  private static final Set<Prefab.ConfigType> SUPPORTED_CONFIG_TYPES = Sets.immutableEnumSet(
    Prefab.ConfigType.CONFIG,
    Prefab.ConfigType.FEATURE_FLAG
  );

  void reportMatch(Match match, LookupContext lookupContext) {
    if (
      SUPPORTED_CONFIG_TYPES.contains(
        match.getConfigElement().getConfig().getConfigType()
      )
    ) {
      if (!matchQueue.offer(new MatchEvent(clock.millis(), match, lookupContext))) {
        //TODO increment dropped events
      }
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
            if (
              !outputQueue.offer(
                new MatchProcessingManager.OutputBuffer(contextsToSend, stats)
              )
            ) {
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

  void flushStats() {
    matchQueue.offer(new MatchProcessor.Event(Event.EventType.FLUSH, clock.millis()));
  }

  static class Event {

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

  static class MatchEvent extends Event {

    Match match;
    LookupContext lookupContext;

    MatchEvent(long timestamp, Match match, LookupContext lookupContext) {
      super(EventType.MATCH, timestamp);
      this.match = match;
      this.lookupContext = lookupContext;
    }
  }

  static class ContextDeduplicator {

    private final Cache<String, String> cache;

    ContextDeduplicator(Duration expiry, int maxSize) {
      this.cache =
        CacheBuilder.newBuilder().expireAfterWrite(expiry).maximumSize(maxSize).build();
    }

    boolean recentlySeen(String fingerprint) {
      if (cache.getIfPresent(fingerprint) != null) {
        return true;
      }
      cache.put(fingerprint, fingerprint);
      return false;
    }
  }
}
