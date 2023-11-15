package cloud.prefab.client.internal;

import cloud.prefab.domain.Prefab;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accepts reports of logger usage by name, level and count and rolls them up for a time window.
 * Flow is as follows
 * 1) reportLoggerUsage is called, constructs a Logger instance and offers it to the queue for a limited time (10ms) to keep logging fast
 * 2) An aggregator thread drains the queue and merges all the Logger instances
 * 3) Merged logger instances (now one per logger name) are merged into the map in the current instance of LogCounts
 * 4) When getAndResetStats is called, the current LogCounts instance is returned, and a new one replaces it with a new starting time set
 * LogCounts contains a mutex so that if the instance is swapped while the aggregator thread is updating it, the caller of getAndResetStats will be blocked
 * on reading it until the update is complete
 */
class LoggerStatsAggregator {

  private static final Logger LOG = LoggerFactory.getLogger(LoggerStatsAggregator.class);

  private final AtomicBoolean running = new AtomicBoolean(false);

  private final Clock clock;

  private final AtomicReference<LogCounts> currentLogCollection = new AtomicReference<>();

  private static final int DRAIN_SIZE = 25_000;
  private static final int QUEUE_SIZE = 1_000_000_000;
  private final List<Prefab.Logger> drain = new ArrayList<>(DRAIN_SIZE); // don't allocate a new one every run

  private final LinkedBlockingQueue<Prefab.Logger> loggerCountQueue = new LinkedBlockingQueue<>(
    QUEUE_SIZE
  );

  private final LongAccumulator dropCounts = new LongAccumulator(Long::sum, 0);

  LoggerStatsAggregator(Clock clock) {
    this.clock = clock;
    currentLogCollection.set(new LogCounts(clock.millis()));
  }

  LogCounts getAndResetStats() {
    return currentLogCollection.getAndSet(new LogCounts(clock.millis()));
  }

  void setStats(LogCounts logCounts) {
    currentLogCollection.set(logCounts);
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
      case FATAL:
        loggerBuilder.setFatals(count);
        break;
    }
    currentLogCollection
      .get()
      .updateLoggerMap(Collections.singleton(loggerBuilder.build()));
  }

  static Prefab.Logger mergeLoggerCollection(Collection<Prefab.Logger> loggers) {
    Iterator<Prefab.Logger> iter = loggers.iterator();
    Prefab.Logger loggerToMutate = iter.next();
    long traces = loggerToMutate.getTraces();
    long debugs = loggerToMutate.getDebugs();
    long infos = loggerToMutate.getInfos();
    long warns = loggerToMutate.getWarns();
    long errors = loggerToMutate.getErrors();
    long fatals = loggerToMutate.getFatals();

    while (iter.hasNext()) {
      Prefab.Logger logger = iter.next();
      traces += logger.getTraces();
      debugs += logger.getDebugs();
      infos += logger.getInfos();
      warns += logger.getWarns();
      errors += logger.getErrors();
      fatals += logger.getFatals();
    }

    return loggerToMutate
      .toBuilder()
      .setTraces(traces)
      .setDebugs(debugs)
      .setInfos(infos)
      .setWarns(warns)
      .setErrors(errors)
      .setFatals(fatals)
      .build();
  }

  static void updateFieldIfNotZero(long total, Consumer<Long> method) {
    if (total > 0) {
      method.accept(total);
    }
  }

  static Prefab.Logger mergeLoggers(Prefab.Logger a, Prefab.Logger b) {
    Prefab.Logger.Builder bldr = Prefab.Logger
      .newBuilder()
      .setLoggerName(a.getLoggerName());
    updateFieldIfNotZero(a.getTraces() + b.getTraces(), bldr::setTraces);
    updateFieldIfNotZero(a.getDebugs() + b.getDebugs(), bldr::setDebugs);
    updateFieldIfNotZero(a.getInfos() + b.getInfos(), bldr::setInfos);
    updateFieldIfNotZero(a.getWarns() + b.getWarns(), bldr::setWarns);
    updateFieldIfNotZero(a.getErrors() + b.getErrors(), bldr::setErrors);
    updateFieldIfNotZero(a.getFatals() + b.getFatals(), bldr::setFatals);
    return bldr.build();
  }

  static class LogCounts {

    private final long startTime;
    private final Map<String, Prefab.Logger> loggerMap;

    private final ReentrantLock mutex = new ReentrantLock();

    LogCounts(long startTime) {
      this.startTime = startTime;
      loggerMap = new HashMap<>();
    }

    long getStartTime() {
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
