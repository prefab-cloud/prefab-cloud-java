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
    executorService.execute(() -> startAPI());

    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    scheduledExecutorService.scheduleAtFixedRate(() -> loadCheckpoint(), 0, checkpointFreq(), TimeUnit.SECONDS);
  }

  public Optional<Prefab.ConfigValue> get(String key) {
    try {
      initializedLatch.await();
      return resolver.getConfigValue(key);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void upsert(Prefab.UpsertRequest upsertRequest) {
    configServiceBlockingStub().upsert(upsertRequest);
  }


  private void loadCheckpoint() {
    String key = baseClient.getApiKey().replace("|", "/");
    final S3Object object = s3Client.getObject(bucket, key);
    try {
      final Prefab.ConfigDeltas configDeltas = Prefab.ConfigDeltas.parseFrom(object.getObjectContent());
      loadDeltas(configDeltas);
    } catch (Exception e) {
      LOG.warn("Issue Loading Checkpoint", e);
    }
  }

  private void startAPI() {
    startAPI(configLoader.getHighwaterMark());
  }

  private void startAPI(long highwaterMark) {
    Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer.newBuilder().setStartAtId(highwaterMark)
        .setAccountId(baseClient.getAccountId())
        .build();

    configServiceStub().getConfig(pointer, new StreamObserver<Prefab.ConfigDeltas>() {
      @Override
      public void onNext(Prefab.ConfigDeltas configDeltas) {
        loadDeltas(configDeltas);
      }

      @Override
      public void onError(Throwable throwable) {
        LOG.warn("Error from API");
        try {
          Thread.sleep(BACKOFF_MILLIS);
        } catch (InterruptedException e) {
          LOG.warn("Interruption Backing Off");
        }
        startAPI();
      }

      @Override
      public void onCompleted() {
        LOG.warn("Unexpected stream completions");
        startAPI();
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

  private long checkpointFreq() {
    try {
      String checkpointFrequency = System.getenv("PREFAB_CHECKPOINT_FREQ_SEC");
      if (!checkpointFrequency.isEmpty()) {
        return Long.parseLong(checkpointFrequency);
      }
    } catch (Exception e) {
      LOG.error(e.getMessage());
    }
    return DEFAULT_CHECKPOINT_SEC;
  }

}
