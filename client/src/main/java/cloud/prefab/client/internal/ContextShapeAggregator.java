package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capture context "shape" and
 */
public class ContextShapeAggregator {

  private static final Logger LOG = LoggerFactory.getLogger(ContextShapeAggregator.class);

  static final long MILLIS_BETWEEN_UPLOADS = TimeUnit.MINUTES.toMillis(20);
  static final long MILLIS_BETWEEN_UPLOADS_WITH_NEW_DATA = TimeUnit.MINUTES.toMillis(5);

  private final PrefabHttpClient prefabHttpClient;
  private final Clock clock;
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> shapes;

  private final AtomicBoolean dirtyFlag = new AtomicBoolean(true);
  private final Optional<String> namespace;

  private long lastUploadTime = 0;

  ContextShapeAggregator(
    Options options,
    PrefabHttpClient prefabHttpClient,
    Clock clock
  ) {
    this.prefabHttpClient = prefabHttpClient;
    this.clock = clock;
    this.shapes = new ConcurrentHashMap<>();
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
      LOG.debug("uploading context shapes");
      prefabHttpClient.reportContextShape(buildProtoShapesFromShapeState());
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

  void reportContextUsage(PrefabContextSetReadable prefabContextSetReadable) {
    Prefab.ContextShapes currentShapes = extractShapes(prefabContextSetReadable);
    for (Prefab.ContextShape contextShape : currentShapes.getShapesList()) {
      ConcurrentHashMap<String, Integer> contextMap = shapes.computeIfAbsent(
        contextShape.getName(),
        key -> new ConcurrentHashMap<>()
      );
      contextShape
        .getFieldTypesMap()
        .forEach((key, value) -> {
          Integer oldValue = contextMap.put(key, value);
          if (!Objects.equals(oldValue, value)) {
            boolean dirtyFlagRaised = dirtyFlag.compareAndSet(false, true);
            if (dirtyFlagRaised && LOG.isTraceEnabled()) {
              LOG.trace(
                "dirty flag raised by context name: {} and property {}",
                contextShape.getName(),
                key
              );
            }
          }
        });
    }
  }

  @VisibleForTesting
  Prefab.ContextShapes buildProtoShapesFromShapeState() {
    Prefab.ContextShapes.Builder shapesBuilder = Prefab.ContextShapes.newBuilder();
    namespace.ifPresent(shapesBuilder::setNamespace);

    shapes.forEach((contextName, contextMap) -> {
      Prefab.ContextShape.Builder shapeBuilder = Prefab.ContextShape
        .newBuilder()
        .setName(contextName);
      shapeBuilder.putAllFieldTypes(contextMap);
      shapesBuilder.addShapes(shapeBuilder);
    });

    return shapesBuilder.build();
  }

  private Prefab.ContextShapes extractShapes(
    PrefabContextSetReadable prefabContextSetReadable
  ) {
    Prefab.ContextShapes.Builder shapesBuilder = Prefab.ContextShapes.newBuilder();
    StreamSupport
      .stream(prefabContextSetReadable.getContexts().spliterator(), false)
      .map(PrefabContext::getShape)
      .forEach(shapesBuilder::addShapes);
    return shapesBuilder.build();
  }
}
