package cloud.prefab.client;

import cloud.prefab.client.config.ConfigLoader;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.Prefab;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.*;

public class ConfigClient {
  private static final Logger LOG = LoggerFactory.getLogger(ConfigClient.class);

  private static final long DEFAULT_CHECKPOINT_SEC = 60;
  private static final long BACKOFF_MILLIS = 3000;

  private final PrefabCloudClient baseClient;
  private final ConfigResolver resolver;
  private final ConfigLoader configLoader;
  private final AmazonS3 s3Client;
  private static final String bucket = "prefab-cloud-checkpoints-prod";
  private CountDownLatch initializedLatch = new CountDownLatch(1);

  public ConfigClient(PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
    configLoader = new ConfigLoader();
    resolver = new ConfigResolver(baseClient, configLoader);

    s3Client = AmazonS3ClientBuilder.standard()
        .withRegion(Regions.US_EAST_1)
        .build();

    ThreadPoolExecutor executor =
        (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

    ExecutorService executorService =
        MoreExecutors.getExitingExecutorService(executor,
            100, TimeUnit.MILLISECONDS);
    executorService.execute(() -> startStreaming());

    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    scheduledExecutorService.scheduleAtFixedRate(() -> loadCheckpoint(), 0, DEFAULT_CHECKPOINT_SEC, TimeUnit.SECONDS);
  }

  public Optional<Prefab.ConfigValue> get(String key) {
    try {
      initializedLatch.await();
      return resolver.getConfigValue(key);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void upsert(String key, Prefab.ConfigValue configValue) {
    Prefab.UpsertRequest upsertRequest = Prefab.UpsertRequest.newBuilder()
        .setAccountId(baseClient.getAccountId())
        .setConfigDelta(Prefab.ConfigDelta.newBuilder()
            .setKey(key)
            .setValue(configValue)
            .build())
        .build();

    configServiceBlockingStub().upsert(upsertRequest);
  }

  private void loadCheckpoint() {
    Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer.newBuilder().setStartAtId(configLoader.getHighwaterMark())
        .setAccountId(baseClient.getAccountId())
        .build();

    configServiceStub().getAllConfig(pointer, new StreamObserver<Prefab.ConfigDeltas>() {
      @Override
      public void onNext(Prefab.ConfigDeltas configDeltas) {
        loadDeltas(configDeltas);
      }

      @Override
      public void onError(Throwable throwable) {
        LOG.warn("Issue getting checkpoint config, falling back to S3");
        loadCheckpointFromS3();
      }

      @Override
      public void onCompleted() {
      }
    });
  }

  private void loadCheckpointFromS3() {
    System.out.println("Loading s3");

    String key = baseClient.getApiKey().replace("|", "/");
    final S3Object object = s3Client.getObject(bucket, key);
    try {
      final Prefab.ConfigDeltas configDeltas = Prefab.ConfigDeltas.parseFrom(object.getObjectContent());
      loadDeltas(configDeltas);
    } catch (Exception e) {
      LOG.warn("Issue Loading Checkpoint", e);
    }
  }

  private void startStreaming() {
    startStreaming(configLoader.getHighwaterMark());
  }

  private void startStreaming(long highwaterMark) {
    Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer.newBuilder().setStartAtId(highwaterMark)
        .setAccountId(baseClient.getAccountId())
        .build();

    configServiceStub().getConfig(pointer, new StreamObserver<Prefab.ConfigDeltas>() {
      @Override
      public void onNext(Prefab.ConfigDeltas configDeltas) {
        System.out.println("stream coming in");
        loadDeltas(configDeltas);
      }

      @Override
      public void onError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException && ((StatusRuntimeException) throwable).getStatus().getCode() == Status.PERMISSION_DENIED.getCode()) {
          LOG.info("Not restarting the stream: {}", throwable.getMessage());
        } else {
          LOG.warn("Error from API: ", throwable);
          try {
            Thread.sleep(BACKOFF_MILLIS);
          } catch (InterruptedException e) {
            LOG.warn("Interruption Backing Off");
          }
          startStreaming();
        }
      }

      @Override
      public void onCompleted() {
        LOG.warn("Unexpected stream completion");
        startStreaming();
      }
    });
  }

  private void loadDeltas(Prefab.ConfigDeltas configDeltas) {
    for (Prefab.ConfigDelta configDelta : configDeltas.getDeltasList()) {
      configLoader.set(configDelta);
    }
    resolver.update();
    LOG.debug("Load Highwater " + configLoader.getHighwaterMark());
    initializedLatch.countDown();
  }

  private ConfigServiceGrpc.ConfigServiceBlockingStub configServiceBlockingStub() {
    return ConfigServiceGrpc.newBlockingStub(baseClient.getChannel());
  }

  private ConfigServiceGrpc.ConfigServiceStub configServiceStub() {
    return ConfigServiceGrpc.newStub(baseClient.getChannel());
  }

}
