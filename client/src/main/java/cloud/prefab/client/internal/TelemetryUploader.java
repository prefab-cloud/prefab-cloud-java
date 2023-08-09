package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.domain.Prefab;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.failsafe.Bulkhead;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import java.net.http.HttpResponse;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetryUploader implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(TelemetryUploader.class);

  private final LinkedBlockingQueue<MatchProcessingManager.OutputBuffer> queue;
  private final PrefabHttpClient prefabHttpClient;

  private final Bulkhead<HttpResponse<Supplier<Prefab.TelemetryEventsResponse>>> bulkhead = Bulkhead
    .<HttpResponse<Supplier<Prefab.TelemetryEventsResponse>>>builder(5)
    .build();

  private final RetryPolicy<HttpResponse<Supplier<Prefab.TelemetryEventsResponse>>> retryPolicy = RetryPolicy
    .<HttpResponse<Supplier<Prefab.TelemetryEventsResponse>>>builder()
    .withMaxRetries(3)
    .withBackoff(1, 10, ChronoUnit.SECONDS)
    .handleResultIf(r -> RETRYABLE_STATUS_CODES.contains(r.statusCode()))
    .build();

  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread uploaderThread;

  TelemetryUploader(
    LinkedBlockingQueue<MatchProcessingManager.OutputBuffer> queue,
    PrefabHttpClient prefabHttpClient,
    Options options
  ) {
    this.prefabHttpClient = prefabHttpClient;
    this.queue = queue;
  }

  private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(429, 500, 503); //TODO add more

  void start() {
    if (queue == null) {
      throw new IllegalStateException("Queue is null");
    }
    if (running.compareAndSet(false, true)) {
      ThreadFactory uploaderFactory = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("prefab-match-processor-uploader-%d")
        .build();

      uploaderThread = uploaderFactory.newThread(this::uploadLoop);
      uploaderThread.setUncaughtExceptionHandler((t, e) ->
        LOG.error("uncaught exception in thread t {}", t.getName(), e)
      );
      uploaderThread.setDaemon(true);
      uploaderThread.start();
    }
  }

  void uploadLoop() {
    //do-while so we can do loop once for tests
    do {
      try {
        bulkhead.acquirePermit();
        MatchProcessingManager.OutputBuffer outputBuffer = queue.take();
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
    } while (running.get());
  }

  @Override
  public void close() {
    if (running.compareAndSet(true, false)) {
      if (uploaderThread != null) {
        uploaderThread.interrupt();
      }
    }
  }
}
