package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.client.config.Match;
import cloud.prefab.domain.Prefab;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetryManager implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(TelemetryManager.class);

  static final int OUTPUT_QUEUE_SIZE = 10;
  static final int INPUT_QUEUE_SIZE = 1_000_000;

  private static final int DRAIN_SIZE = 25_000;
  private final List<IncomingTelemetryEvent> drain = new ArrayList<>(DRAIN_SIZE); // don't allocate a new one every run
  private final LongAccumulator droppedEventCount = new LongAccumulator(Long::sum, 0);

  /*
  this pokes events into a stable of aggregators
  then periodically gets their aggregated results and posts to the prefab api
   */

  private final LoggerStatsAggregator loggerStatsAggregator;
  private final MatchStatsAggregator matchStatsAggregator;
  private final ContextShapeAggregator contextShapeAggregator;
  private final ExampleContextBuffer exampleContextBuffer;
  private final Options options;
  private final TelemetryUploader telemetryUploader;

  private final LinkedBlockingQueue<OutputBuffer> outputQueue = new LinkedBlockingQueue<>(
    TelemetryManager.OUTPUT_QUEUE_SIZE
  );
  private final LinkedBlockingQueue<IncomingTelemetryEvent> inputQueue = new LinkedBlockingQueue<>(
    TelemetryManager.INPUT_QUEUE_SIZE
  );
  private final Clock clock;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicLong recordingPeriodStartTime = new AtomicLong();

  TelemetryManager(
    LoggerStatsAggregator loggerStatsAggregator,
    MatchStatsAggregator matchStatsAggregator,
    ContextShapeAggregator contextShapeAggregator,
    ExampleContextBuffer exampleContextBuffer,
    PrefabHttpClient prefabHttpClient,
    Options options,
    Clock clock
  ) {
    this.loggerStatsAggregator = loggerStatsAggregator;
    this.matchStatsAggregator = matchStatsAggregator;
    this.contextShapeAggregator = contextShapeAggregator;
    this.exampleContextBuffer = exampleContextBuffer;
    this.options = options;
    this.telemetryUploader =
      new TelemetryUploader(outputQueue, prefabHttpClient, options);
    this.clock = clock;
  }

  void start(int autoFlushSeconds) {
    // check and set already running
    // start thread continuously running eventloop()
    if (running.compareAndSet(false, true)) {
      telemetryUploader.start();
      // start event loop thread
      ThreadFactory aggregatorFactory = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("prefab-telemetry-manager-%d")
        .build();

      Thread aggregatorThread = aggregatorFactory.newThread(this::eventLoop);
      aggregatorThread.setDaemon(true);
      aggregatorThread.setUncaughtExceptionHandler((t, e) ->
        LOG.error("uncaught exception in thread t {}", t.getName(), e)
      );
      aggregatorThread.start();
      recordingPeriodStartTime.set(clock.millis());
      // start scheduled flush thread
      if (autoFlushSeconds > 0) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
          1,
          r -> new Thread(r, "prefab-telemetry-manager-autoflush")
        );
        ScheduledExecutorService executorService = MoreExecutors.getExitingScheduledExecutorService(
          executor,
          100,
          TimeUnit.MILLISECONDS
        );
        executorService.scheduleWithFixedDelay(
          () -> {
            try {
              requestFlush();
            } catch (Exception e) {
              LOG.debug("error requesting flush", e);
            }
          },
          autoFlushSeconds,
          autoFlushSeconds,
          TimeUnit.SECONDS
        );
      }
    }
  }

  void start() {
    start(options.getTelemetryUploadIntervalSeconds());
  }

  void reportMatch(String configKey, @Nullable Match match, LookupContext lookupContext) {
    if (match == null) {
      return;
    }
    long now = clock.millis();
    if (!inputQueue.offer(new MatchEvent(now, configKey, match, lookupContext))) {
      droppedEventCount.accumulate(1);
    }
  }

  void reportLoggerUsage(String loggerName, Prefab.LogLevel logLevel, long count) {
    if (
      !inputQueue.offer(new LoggingEvent(clock.millis(), loggerName, logLevel, count))
    ) {
      droppedEventCount.accumulate(1);
    }
  }

  private void handleLogEvent(IncomingTelemetryEvent incomingTelemetryEvent) {
    LoggingEvent loggingEvent = (LoggingEvent) incomingTelemetryEvent;
    if (options.isCollectLoggerCounts()) {
      loggerStatsAggregator.reportLoggerUsage(
        loggingEvent.loggerName,
        loggingEvent.logLevel,
        loggingEvent.count
      );
    }
  }

  private void handleMatchEvent(IncomingTelemetryEvent telemetryEvent) {
    MatchEvent matchEvent = (MatchEvent) telemetryEvent;
    if (!matchEvent.lookupContext.getPrefabContextSet().isEmpty()) {
      if (options.isCollectContextShapeEnabled()) {
        contextShapeAggregator.reportContextUsage(
          matchEvent.lookupContext.getPrefabContextSet()
        );
      }
      if (options.isCollectExampleContextEnabled()) {
        exampleContextBuffer.recordContext(
          matchEvent.timestamp,
          matchEvent.lookupContext.getPrefabContextSet()
        );
      }
    }

    if (
      matchEvent.match != null &&
      options.isCollectEvaluationSummaries() &&
      !matchEvent.match.getConfigValue().getConfidential()
    ) {
      matchStatsAggregator.recordMatch(matchEvent.match, matchEvent.timestamp);
    }
  }

  private void handleFlush(IncomingTelemetryEvent telemetryEvent) {
    FlushEvent flushEvent = (FlushEvent) telemetryEvent;
    // build an output buffer by retrieving data from all the aggregators/buffers
    MatchStatsAggregator.StatsAggregate matchStats = matchStatsAggregator.getAndResetStatsAggregate();
    Set<Prefab.ExampleContext> exampleContexts = exampleContextBuffer.getAndResetContexts();
    LoggerStatsAggregator.LogCounts loggerCounts = loggerStatsAggregator.getAndResetStats();
    Optional<Prefab.ContextShapes> contextShapesMaybe = contextShapeAggregator.getShapesIfNewInfo();
    long currentDroppedEventCount = droppedEventCount.getThenReset();
    long previousReportingPeriodStart = recordingPeriodStartTime.get();
    long now = clock.millis();
    recordingPeriodStartTime.set(now);

    if (
      !outputQueue.offer(
        new OutputBuffer(
          previousReportingPeriodStart,
          now,
          exampleContexts,
          matchStats,
          loggerCounts.getLoggerMap().values(),
          contextShapesMaybe,
          currentDroppedEventCount,
          flushEvent.future
        )
      )
    ) {
      recordingPeriodStartTime.set(previousReportingPeriodStart);
      // push states back to aggregators and try again
      matchStatsAggregator.setStatsAggregate(matchStats);
      droppedEventCount.accumulate(currentDroppedEventCount);
      recordingPeriodStartTime.set(previousReportingPeriodStart);
      exampleContextBuffer.setContexts(exampleContexts);
      loggerStatsAggregator.setStats(loggerCounts);
      flushEvent.future.complete(false);
    }
  }

  CompletableFuture<Boolean> requestFlush() {
    FlushEvent flushEvent = new FlushEvent(clock.millis());
    if (!inputQueue.offer(flushEvent)) {
      return CompletableFuture.completedFuture(false);
    }
    return flushEvent.future;
  }

  void eventLoop() {
    do {
      try {
        IncomingTelemetryEvent incomingTelemetryEvent = inputQueue.poll(
          500,
          TimeUnit.MILLISECONDS
        );
        if (incomingTelemetryEvent != null) {
          drain.add(incomingTelemetryEvent);
          inputQueue.drainTo(drain, DRAIN_SIZE - 1);
          for (IncomingTelemetryEvent telemetryEvent : drain) {
            switch (telemetryEvent.eventType) {
              case LOG:
                handleLogEvent(telemetryEvent);
                break;
              case MATCH:
                handleMatchEvent(telemetryEvent);
                break;
              case FLUSH:
                handleFlush(telemetryEvent);
                break;
            }
          }
          drain.clear();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    } while (running.get());
  }

  @Override
  public void close() throws Exception {
    running.set(false);
  }

  static class OutputBuffer {

    static final Logger LOG = LoggerFactory.getLogger(OutputBuffer.class);
    private final Collection<Prefab.Logger> loggerCollection;
    private final Optional<Prefab.ContextShapes> contextShapesMaybe;
    private final long droppedEventCount;
    private final CompletableFuture<Boolean> uploadCompleteFuture;

    private final long startTime;
    private final long endTime;
    Set<Prefab.ExampleContext> recentlySeenContexts;
    MatchStatsAggregator.StatsAggregate statsAggregate;

    public OutputBuffer(
      long startTime,
      long endTime,
      Set<Prefab.ExampleContext> recentlySeenContexts,
      MatchStatsAggregator.StatsAggregate statsAggregate,
      Collection<Prefab.Logger> loggerCollection,
      Optional<Prefab.ContextShapes> contextShapes,
      long droppedEventCount,
      CompletableFuture<Boolean> uploadCompleteFuture
    ) {
      this.startTime = startTime;
      this.endTime = endTime;
      this.recentlySeenContexts = recentlySeenContexts;
      this.statsAggregate = statsAggregate;
      this.loggerCollection = loggerCollection;
      this.contextShapesMaybe = contextShapes;
      this.droppedEventCount = droppedEventCount;
      this.uploadCompleteFuture = uploadCompleteFuture;
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
        telemetryEventsBuilder.addEvents(
          Prefab.TelemetryEvent
            .newBuilder()
            .setClientStats(
              Prefab.ClientStats
                .newBuilder()
                .setDroppedEventCount(droppedEventCount)
                .setStart(startTime)
                .setEnd(endTime)
                .build()
            )
        );
      }
      if (!statsAggregate.getCounterData().isEmpty()) {
        telemetryEventsBuilder.addEvents(
          Prefab.TelemetryEvent.newBuilder().setSummaries(statsAggregate.toProto())
        );
      }

      if (!loggerCollection.isEmpty()) {
        Prefab.LoggersTelemetryEvent telemetryEvent = Prefab.LoggersTelemetryEvent
          .newBuilder()
          .addAllLoggers(loggerCollection)
          .build();
        telemetryEventsBuilder.addEvents(
          Prefab.TelemetryEvent.newBuilder().setLoggers(telemetryEvent)
        );
      }
      contextShapesMaybe.ifPresent(contextShapes -> {
        if (!contextShapes.getShapesList().isEmpty()) {
          telemetryEventsBuilder.addEvents(
            Prefab.TelemetryEvent.newBuilder().setContextShapes(contextShapes)
          );
        }
      });

      return telemetryEventsBuilder.build();
    }

    void complete() {
      uploadCompleteFuture.complete(true);
    }
  }

  static class IncomingTelemetryEvent {

    enum EventType {
      MATCH,
      LOG,
      FLUSH,
    }

    EventType eventType;
    long timestamp;

    IncomingTelemetryEvent(EventType eventType, long timestamp) {
      this.eventType = eventType;
      this.timestamp = timestamp;
    }
  }

  static class MatchEvent extends IncomingTelemetryEvent {

    private final String configKey;

    @Nullable
    Match match;

    LookupContext lookupContext;

    MatchEvent(
      long timestamp,
      String configKey,
      @Nullable Match match,
      LookupContext lookupContext
    ) {
      super(EventType.MATCH, timestamp);
      this.configKey = configKey;
      this.match = match;
      this.lookupContext = lookupContext;
    }
  }

  static class LoggingEvent extends IncomingTelemetryEvent {

    private final String loggerName;
    private final Prefab.LogLevel logLevel;
    private final long count;
    Match match;
    LookupContext lookupContext;

    LoggingEvent(
      long timestamp,
      String loggerName,
      Prefab.LogLevel logLevel,
      long count
    ) {
      super(EventType.LOG, timestamp);
      this.loggerName = loggerName;
      this.logLevel = logLevel;
      this.count = count;
    }
  }

  static class FlushEvent extends IncomingTelemetryEvent {

    private final CompletableFuture<Boolean> future;

    FlushEvent(long timestamp) {
      super(EventType.FLUSH, timestamp);
      this.future = new CompletableFuture<Boolean>();
    }
  }
}
