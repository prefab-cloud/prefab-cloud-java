package cloud.prefab.client.internal;

import cloud.prefab.domain.Prefab;
import cloud.prefab.sse.SSEHandler;
import cloud.prefab.sse.events.DataEvent;
import cloud.prefab.sse.events.Event;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SseConfigStreamingSubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(
    SseConfigStreamingSubscriber.class
  );

  private final PrefabHttpClient prefabHttpClient;
  private final Supplier<Long> highwaterMarkSupplier;
  private final Consumer<Prefab.Configs> configsConsumer;
  private final ScheduledExecutorService scheduledExecutorService;

  public SseConfigStreamingSubscriber(
    PrefabHttpClient prefabHttpClient,
    Supplier<Long> highwaterMarkSupplier,
    Consumer<Prefab.Configs> configsConsumer,
    ScheduledExecutorService scheduledExecutorService
  ) {
    this.prefabHttpClient = prefabHttpClient;
    this.highwaterMarkSupplier = highwaterMarkSupplier;
    this.configsConsumer = configsConsumer;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  public void start() {
    restart(0);
  }

  private void restart(int errorCount) {
    Runnable starter = () -> {
      try {
        SSEHandler sseHandler = new SSEHandler();
        FlowSubscriber flowSubscriber = new FlowSubscriber(
          configsConsumer,
          hasReceivedData -> restart(hasReceivedData ? 1 : errorCount + 1)
        );
        sseHandler.subscribe(flowSubscriber);
        prefabHttpClient.requestConfigSSE(highwaterMarkSupplier.get(), sseHandler);
      } catch (Exception e) {
        if (e.getMessage().contains("GOAWAY")) {
          LOG.debug("Got GOAWAY on SSE config stream, will restart connection.");
        } else {
          LOG.warn("Unexpected exception starting SSE config stream, will retry", e);
        }
      }
    };

    if (errorCount == 0) {
      starter.run();
    } else {
      long delayMillis = RetryDelayCalculator.exponentialMillisToNextTry(
        errorCount,
        TimeUnit.SECONDS.toMillis(1),
        TimeUnit.SECONDS.toMillis(30)
      );
      LOG.info("Restarting SSE config connection in {} ms", delayMillis);
      scheduledExecutorService.schedule(starter, delayMillis, TimeUnit.MILLISECONDS);
    }
  }

  static class FlowSubscriber implements Flow.Subscriber<Event> {

    private final Consumer<Prefab.Configs> configConsumer;
    private final Consumer<Boolean> restartHandler;
    private Flow.Subscription subscription;

    private final AtomicBoolean hasReceivedData = new AtomicBoolean(false);

    FlowSubscriber(
      Consumer<Prefab.Configs> configConsumer,
      Consumer<Boolean> restartHandler
    ) {
      this.configConsumer = configConsumer;
      this.restartHandler = restartHandler;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(1);
    }

    @Override
    public void onNext(Event item) {
      if (item instanceof DataEvent) {
        DataEvent dataEvent = (DataEvent) item;
        try {
          hasReceivedData.set(true);
          String dataPayload = dataEvent.getData().trim();
          if (!dataPayload.isEmpty()) {
            Prefab.Configs configs = Prefab.Configs.parseFrom(
              Base64.getDecoder().decode(dataPayload)
            );
            if (!configs.hasConfigServicePointer()) {
              LOG.debug("Ignoring empty config keep-alive");
            } else {
              configConsumer.accept(configs);
            }
          }
        } catch (InvalidProtocolBufferException e) {
          LOG.warn(
            "Error parsing configs from event name {} - error message {}",
            dataEvent.getEventName(),
            e.getMessage()
          );
        }
      }
      subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
      LOG.info("Unexpected error encountered", throwable);
      if (Optional.ofNullable(throwable.getMessage()).orElse("").contains("GOAWAY")) {
        LOG.debug("Got GOAWAY on SSE config stream, will restart connection.");
      } else {
        LOG.warn("Unexpected exception from SSE config stream, will retry", throwable);
      }
      restartHandler.accept(getHasReceivedData());
    }

    @Override
    public void onComplete() {
      // this is called even on auth failure
      LOG.info("Unexpected stream completion");
      restartHandler.accept(getHasReceivedData());
    }

    public boolean getHasReceivedData() {
      return hasReceivedData.get();
    }
  }
}
