package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.domain.Prefab;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluatedKeysAggregator {

  private static final Logger LOG = LoggerFactory.getLogger(
    EvaluatedKeysAggregator.class
  );

  static final long MILLIS_BETWEEN_UPLOADS = TimeUnit.MINUTES.toMillis(20);
  static final long MILLIS_BETWEEN_UPLOADS_WITH_NEW_DATA = TimeUnit.MINUTES.toMillis(5);

  private final PrefabHttpClient prefabHttpClient;
  private final Clock clock;
  private final Set<String> evaluatedKeys;

  private final AtomicBoolean dirtyFlag = new AtomicBoolean(true);
  private final Optional<String> namespace;

  private long lastUploadTime = 0;

  EvaluatedKeysAggregator(
    Options options,
    PrefabHttpClient prefabHttpClient,
    Clock clock
  ) {
    this.prefabHttpClient = prefabHttpClient;
    this.clock = clock;
    // from https://www.baeldung.com/java-concurrent-hashset-concurrenthashmap
    this.evaluatedKeys = ConcurrentHashMap.newKeySet();
    this.namespace = options.getNamespace();
  }

  void start() {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
      1,
      r -> new Thread(r, "prefab-context-shapes-aggregator")
    );
    ScheduledExecutorService executorService = MoreExecutors.getExitingScheduledExecutorService(
      executor,
      100,
      TimeUnit.MILLISECONDS
    );
    executorService.scheduleWithFixedDelay(
      () -> {
        try {
          doUpload();
        } catch (Exception e) {
          LOG.debug("error uploading context shapes", e);
        }
      },
      1,
      1,
      TimeUnit.MINUTES
    );
  }

  @VisibleForTesting
  void doUpload() {
    if (shouldUpload()) {
      Prefab.EvaluatedKeys.Builder builder = Prefab.EvaluatedKeys
        .newBuilder()
        .addAllKeys(evaluatedKeys);
      namespace.ifPresent(builder::setNamespace);
      prefabHttpClient.reportEvaluatedKeys(builder.build());
      lastUploadTime = clock.millis();
      dirtyFlag.set(false);
    }
  }

  private boolean shouldUpload() {
    if (lastUploadTime == 0) {
      return true;
    }
    long millisSinceLastUpload = clock.millis() - lastUploadTime;
    if (millisSinceLastUpload >= MILLIS_BETWEEN_UPLOADS) {
      return true;
    }
    if (
      millisSinceLastUpload >= MILLIS_BETWEEN_UPLOADS_WITH_NEW_DATA && dirtyFlag.get()
    ) {
      return true;
    }
    LOG.debug(
      "Skipping upload, minutes since upload is {} and dirtyFlag is {}",
      TimeUnit.MILLISECONDS.toMinutes(millisSinceLastUpload),
      dirtyFlag.get()
    );
    return false;
  }

  void reportKeyUsage(String key) {
    if (evaluatedKeys.add(key)) {
      boolean dirtyFlagRaised = dirtyFlag.compareAndSet(false, true);
      if (dirtyFlagRaised && LOG.isTraceEnabled()) {
        LOG.trace("dirty flag raised by key {}", key);
      }
    }
  }
}
