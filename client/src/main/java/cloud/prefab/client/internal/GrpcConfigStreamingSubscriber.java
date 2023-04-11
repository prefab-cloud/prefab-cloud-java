package cloud.prefab.client.internal;

import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.Prefab;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelProvider;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcConfigStreamingSubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(
    GrpcConfigStreamingSubscriber.class
  );

  private final Supplier<ManagedChannel> managedChannelSupplier;
  private final Supplier<Long> highwaterMarkSupplier;
  private final Consumer<Prefab.Configs> configsConsumer;
  private final ScheduledExecutorService scheduledExecutorService;

  public GrpcConfigStreamingSubscriber(
    Supplier<ManagedChannel> managedChannelSupplier,
    Supplier<Long> highwaterMarkSupplier,
    Consumer<Prefab.Configs> configsConsumer,
    ScheduledExecutorService scheduledExecutorService
  ) {
    this.managedChannelSupplier = managedChannelSupplier;
    this.highwaterMarkSupplier = highwaterMarkSupplier;
    this.configsConsumer = configsConsumer;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  public void start() {
    restart(new StreamObserver(configsConsumer, this::restart));
  }

  private void restart(StreamObserver streamObserver) {
    Runnable starter = () -> {
      try {
        configServiceStub()
          .getConfig(
            Prefab.ConfigServicePointer
              .newBuilder()
              .setStartAtId(highwaterMarkSupplier.get())
              .build(),
            streamObserver
          );
      } catch (ManagedChannelProvider.ProviderNotFoundException e) {
        LOG.warn("GRPC implementation missing {}", e.getMessage());
      }
    };
    if (streamObserver.getErrorCount() == 0) {
      starter.run();
    } else {
      long delayMillis = RetryDelayCalculator.exponentialMillisToNextTry(
        streamObserver.getErrorCount(),
        TimeUnit.SECONDS.toMillis(1),
        TimeUnit.SECONDS.toMillis(30)
      );
      LOG.info("Restarting connection in {} ms", delayMillis);
      scheduledExecutorService.schedule(starter, delayMillis, TimeUnit.MILLISECONDS);
    }
  }

  static class StreamObserver implements io.grpc.stub.StreamObserver<Prefab.Configs> {

    private final Consumer<Prefab.Configs> configConsumer;
    private final Consumer<StreamObserver> restartRequester;

    StreamObserver(
      Consumer<Prefab.Configs> configConsumer,
      Consumer<StreamObserver> restartRequester
    ) {
      this.configConsumer = configConsumer;
      this.restartRequester = restartRequester;
    }

    AtomicInteger errorCount = new AtomicInteger(0);

    public int getErrorCount() {
      return errorCount.get();
    }

    @Override
    public void onNext(Prefab.Configs configs) {
      configConsumer.accept(configs);
      errorCount.set(0);
    }

    @Override
    public void onError(Throwable throwable) {
      errorCount.incrementAndGet();
      if (
        throwable instanceof StatusRuntimeException &&
        ((StatusRuntimeException) throwable).getStatus().getCode() ==
        Status.PERMISSION_DENIED.getCode()
      ) {
        LOG.info("Not restarting the stream: {}", throwable.getMessage());
      } else {
        LOG.warn("Error from streaming API will restart streaming connection");
        LOG.debug("Details of streaming API failure are", throwable);
        restartRequester.accept(this);
      }
    }

    @Override
    public void onCompleted() {
      errorCount.incrementAndGet();
      LOG.warn("Unexpected stream completion");
      restartRequester.accept(this);
    }
  }

  private ConfigServiceGrpc.ConfigServiceStub configServiceStub() {
    return ConfigServiceGrpc.newStub(managedChannelSupplier.get());
  }
}
