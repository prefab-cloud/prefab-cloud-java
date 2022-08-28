package cloud.prefab.client;

import cloud.prefab.client.config.ConfigLoader;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.Prefab;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigClient implements ConfigStore {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigClient.class);

  private static final long DEFAULT_CHECKPOINT_SEC = 60;
  private static final long BACKOFF_MILLIS = 3000;

  private final PrefabCloudClient baseClient;
  private final ConfigResolver resolver;
  private final ConfigLoader configLoader;
  private static final String DEFAULT_S3CF_BUCKET =
    "http://d2j4ed6ti5snnd.cloudfront.net";
  private final String cfS3Url;

  private final CountDownLatch initializedLatch = new CountDownLatch(1);

  private enum Source {
    S3,
    API,
    STREAMING,
  }

  public ConfigClient(PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
    configLoader = new ConfigLoader();
    resolver = new ConfigResolver(baseClient, configLoader);

    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

    ExecutorService executorService = MoreExecutors.getExitingExecutorService(
      executor,
      100,
      TimeUnit.MILLISECONDS
    );
    executorService.execute(() -> startStreaming());

    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(
      1
    );
    scheduledExecutorService.scheduleAtFixedRate(
      () -> loadCheckpoint(),
      0,
      DEFAULT_CHECKPOINT_SEC,
      TimeUnit.SECONDS
    );

    String key = baseClient.getApiKey().replace("|", "/");
    final String s3Cloudfront = Optional
      .ofNullable(System.getenv("PREFAB_S3CF_BUCKET"))
      .orElse(DEFAULT_S3CF_BUCKET);
    this.cfS3Url = String.format("%s/%s", s3Cloudfront, key);
  }

  @Override
  public Optional<Prefab.ConfigValue> get(String key) {
    try {
      initializedLatch.await();
      return resolver.getConfigValue(key);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void upsert(String key, Prefab.ConfigValue configValue) {
    Prefab.Config upsertRequest = Prefab.Config
      .newBuilder()
      .setKey(key)
      .addRows(Prefab.ConfigRow.newBuilder().setValue(configValue).build())
      .build();

    configServiceBlockingStub().upsert(upsertRequest);
  }

  public void upsert(Prefab.Config config) {
    configServiceBlockingStub().upsert(config);
  }

  @Override
  public Optional<Prefab.Config> getConfigObj(String key) {
    try {
      initializedLatch.await();
      return resolver.getConfig(key);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Collection<String> getKeys() {
    try {
      initializedLatch.await();
      return resolver.getKeys();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void loadCheckpoint() {
    Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer
      .newBuilder()
      .setStartAtId(configLoader.getHighwaterMark())
      .build();

    configServiceStub()
      .getAllConfig(
        pointer,
        new StreamObserver<>() {
          @Override
          public void onNext(Prefab.Configs configs) {
            loadConfigs(configs, Source.API);
          }

          @Override
          public void onError(Throwable throwable) {
            LOG.warn("Issue getting checkpoint config, falling back to S3", throwable);
            loadCheckpointFromS3();
          }

          @Override
          public void onCompleted() {}
        }
      );
  }

  private void loadCheckpointFromS3() {
    LOG.info("Loading from S3");

    try {
      HttpURLConnection urlConnection = (HttpURLConnection) new URL(cfS3Url)
        .openConnection();
      urlConnection.setConnectTimeout(5000);
      urlConnection.setReadTimeout(30000);

      int responseCode = urlConnection.getResponseCode();
      InputStream inputStream = urlConnection.getInputStream();
      try {
        if (responseCode == 200) {
          Prefab.Configs configs = Prefab.Configs.parseFrom(inputStream);
          loadConfigs(configs, Source.S3);
        } else {
          LOG.warn(
            "Issue Loading Checkpoint. This may not be available for your plan. Status code {}",
            responseCode
          );
        }
      } finally {
        Closeables.closeQuietly(inputStream);
      }
    } catch (IOException e) {
      LOG.warn("Issue Loading Checkpoint. This may not be available for your plan.", e);
    }
  }

  private void startStreaming() {
    startStreaming(configLoader.getHighwaterMark());
  }

  private void startStreaming(long highwaterMark) {
    Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer
      .newBuilder()
      .setStartAtId(highwaterMark)
      .build();

    configServiceStub()
      .getConfig(
        pointer,
        new StreamObserver<Prefab.Configs>() {
          @Override
          public void onNext(Prefab.Configs configs) {
            loadConfigs(configs, Source.STREAMING);
          }

          @Override
          public void onError(Throwable throwable) {
            if (
              throwable instanceof StatusRuntimeException &&
              ((StatusRuntimeException) throwable).getStatus().getCode() ==
              Status.PERMISSION_DENIED.getCode()
            ) {
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
            try {
              Thread.sleep(10000);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            startStreaming();
          }
        }
      );
  }

  private void loadConfigs(Prefab.Configs configs, Source source) {
    LOG.info(
      "Load {} configs from {} in env {}",
      configs.getConfigsCount(),
      source,
      configs.getConfigServicePointer().getProjectEnvId()
    );
    resolver.setProjectEnvId(configs.getConfigServicePointer().getProjectEnvId());

    for (Prefab.Config config : configs.getConfigsList()) {
      configLoader.set(config);
    }
    resolver.update();
    LOG.info("Loaded {} at highwater {} ", source, configLoader.getHighwaterMark());

    if (initializedLatch.getCount() > 0) {
      LOG.info(
        "Initialized Prefab from {} at highwater {}",
        source,
        configLoader.getHighwaterMark()
      );
      initializedLatch.countDown();
    }
  }

  private ConfigServiceGrpc.ConfigServiceBlockingStub configServiceBlockingStub() {
    return ConfigServiceGrpc.newBlockingStub(baseClient.getChannel());
  }

  private ConfigServiceGrpc.ConfigServiceStub configServiceStub() {
    return ConfigServiceGrpc.newStub(baseClient.getChannel());
  }
}
