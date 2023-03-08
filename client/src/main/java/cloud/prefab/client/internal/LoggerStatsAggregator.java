package cloud.prefab.client.internal;

import cloud.prefab.domain.Prefab;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LoggerStatsAggregator {

  private static final Logger LOG = LoggerFactory.getLogger(LoggerStatsAggregator.class);

  private final Clock clock;

  private final AtomicReference<LogCounts> currentLogCollection = new AtomicReference<>();

  LoggerStatsAggregator(Clock clock) {
    this.clock = clock;
    currentLogCollection.set(new LogCounts(clock.millis()));
  }

  private final LinkedBlockingQueue<Prefab.Logger> loggerCountQueue = new LinkedBlockingQueue<>(
    100_000
  );

  void start() {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
      1,
      r -> new Thread(r, "prefab-log-stats-aggregator")
    );
    ScheduledExecutorService executorService = MoreExecutors.getExitingScheduledExecutorService(
      executor,
      100,
      TimeUnit.MILLISECONDS
    );
    executorService.scheduleAtFixedRate(
      () -> {
        try {
          aggregate();
        } catch (Exception e) {
          LOG.debug("error in aggregator", e);
        }
      },
      100,
      100,
      TimeUnit.MILLISECONDS
    );
  }

  @VisibleForTesting
  void aggregate() {
    int drainSize = 10_000;
    List<Prefab.Logger> drain = new ArrayList<>(drainSize);
    int drainCount = loggerCountQueue.drainTo(drain, drainSize);
    if (drainCount > 0) {
      Map<String, Prefab.Logger> aggregates = drain
        .stream()
        .collect(
          Collectors.groupingBy(
            Prefab.Logger::getLoggerName,
            Collectors.collectingAndThen(
              Collectors.toList(),
              LoggerStatsAggregator::mergeLoggerCollection
            )
          )
        );

      currentLogCollection.get().updateLoggerMap(aggregates.values());
    }
  }

  LogCounts getAndResetStats() {
    return currentLogCollection.getAndSet(new LogCounts(clock.millis()));
  }

  void reportLoggerUsage(String loggerName, Prefab.LogLevel logLevel, long count) {
    Prefab.Logger.Builder loggerBuilder = Prefab.Logger
      .newBuilder()
      .setLoggerName(loggerName);
    switch (logLevel) {
      case TRACE:
        loggerBuilder.setTraces(count);
        break;
      case DEBUG:
        loggerBuilder.setDebugs(count);
        break;
      case INFO:
        loggerBuilder.setInfos(count);
        break;
      case WARN:
        loggerBuilder.setWarns(count);
        break;
      case ERROR:
        loggerBuilder.setErrors(count);
        break;
    }

    try {
      loggerCountQueue.offer(loggerBuilder.build(), 10, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static Prefab.Logger mergeLoggerCollection(Collection<Prefab.Logger> loggers) {
    Iterator<Prefab.Logger> iter = loggers.iterator();
    Prefab.Logger loggerToMutate = iter.next();
    long traces = loggerToMutate.getTraces();
    long debugs = loggerToMutate.getDebugs();
    long infos = loggerToMutate.getInfos();
    long warns = loggerToMutate.getWarns();
    long errors = loggerToMutate.getErrors();

    while (iter.hasNext()) {
      Prefab.Logger logger = iter.next();
      traces += logger.getTraces();
      debugs += logger.getDebugs();
      infos += logger.getInfos();
      warns += logger.getWarns();
      errors += logger.getErrors();
    }

    return loggerToMutate
      .toBuilder()
      .setTraces(traces)
      .setDebugs(debugs)
      .setInfos(infos)
      .setWarns(warns)
      .setErrors(errors)
      .build();
  }

  static Prefab.Logger mergeLoggers(Prefab.Logger a, Prefab.Logger b) {
    return a
      .toBuilder()
      .setTraces(a.getTraces() + b.getTraces())
      .setDebugs(a.getDebugs() + b.getDebugs())
      .setInfos(a.getInfos() + b.getInfos())
      .setWarns(a.getWarns() + b.getWarns())
      .setErrors(a.getErrors() + b.getErrors())
      .build();
  }

  static class LogCounts {

    private final long startTime;
    private final Map<String, Prefab.Logger> loggerMap;

    private final ReentrantLock mutex = new ReentrantLock();

    LogCounts(long startTime) {
      this.startTime = startTime;
      loggerMap = new HashMap<>();
    }

    public long getStartTime() {
      return startTime;
    }

    Map<String, Prefab.Logger> getLoggerMap() {
      try {
        mutex.lock();
        return loggerMap;
      } finally {
        mutex.unlock();
      }
    }

    void updateLoggerMap(Collection<Prefab.Logger> loggers) {
      try {
        mutex.lock();
        for (Prefab.Logger logger : loggers) {
          loggerMap.merge(
            logger.getLoggerName(),
            logger,
            LoggerStatsAggregator::mergeLoggers
          );
        }
      } finally {
        mutex.unlock();
      }
    }
  }
}
