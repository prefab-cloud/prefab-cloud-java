package cloud.prefab.client;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.MoreExecutors;

import cloud.prefab.client.config.ConfigLoader;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.Prefab;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class ConfigClient implements ConfigStore {
  private static final Logger LOG = LoggerFactory.getLogger(ConfigClient.class);

  private static final long DEFAULT_CHECKPOINT_SEC = 60;
  private static final long BACKOFF_MILLIS = 3000;

  private final PrefabCloudClient baseClient;
  private final ConfigResolver resolver;
  private final ConfigLoader configLoader;
  private static final String DEFAULT_S3CF_BUCKET = "http://d2j4ed6ti5snnd.cloudfront.net";
  private final CloseableHttpClient httpclient;
  private final String cfS3Url;

  private CountDownLatch initializedLatch = new CountDownLatch(1);

  private enum Source {S3, API, STREAMING}

  public ConfigClient(PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
    configLoader = new ConfigLoader();
    resolver = new ConfigResolver(baseClient, configLoader);

    ThreadPoolExecutor executor =
        (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

    ExecutorService executorService =
        MoreExecutors.getExitingExecutorService(executor,
            100, TimeUnit.MILLISECONDS);
    executorService.execute(() -> startStreaming());
    httpclient = HttpClients.createDefault();

    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    scheduledExecutorService.scheduleAtFixedRate(() -> loadCheckpoint(), 0, DEFAULT_CHECKPOINT_SEC, TimeUnit.SECONDS);

    String key = baseClient.getApiKey().replace("|", "/");
    final String s3Cloudfront = Optional.ofNullable(System.getenv("PREFAB_S3CF_BUCKET")).orElse(DEFAULT_S3CF_BUCKET);
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
    Prefab.Config upsertRequest = Prefab.Config.newBuilder()
        .setProjectId(baseClient.getProjectId())
        .setKey(key)
        .addRows(Prefab.ConfigRow.newBuilder()
            .setValue(configValue)
            .build())
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
  public long getProjectId() {
    return baseClient.getProjectId();
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
    Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer.newBuilder().setStartAtId(configLoader.getHighwaterMark())
        .build();

    configServiceStub().getAllConfig(pointer, new StreamObserver<Prefab.Configs>() {
      @Override
      public void onNext(Prefab.Configs configs) {
        loadConfigs(configs, Source.API);
      }

      @Override
      public void onError(Throwable throwable) {
        LOG.warn(throwable.getMessage());
        LOG.warn("Issue getting checkpoint config, falling back to S3");
        loadCheckpointFromS3();
      }

      @Override
      public void onCompleted() {
      }
    });
  }

  private void loadCheckpointFromS3() {
    LOG.info("Loading from S3");

    HttpGet httpGet = new HttpGet(cfS3Url);

    try {
      CloseableHttpResponse response1 = httpclient.execute(httpGet);
      final Prefab.Configs configs = Prefab.Configs.parseFrom(response1.getEntity().getContent());
      loadConfigs(configs, Source.S3);
    } catch (Exception e) {
      LOG.warn("Issue Loading Checkpoint. This may not be available for your plan.", e);
    }
  }

  private void startStreaming() {
    startStreaming(configLoader.getHighwaterMark());
  }

  private void startStreaming(long highwaterMark) {
    Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer.newBuilder().setStartAtId(highwaterMark)
        .build();

    configServiceStub().getConfig(pointer, new StreamObserver<Prefab.Configs>() {
      @Override
      public void onNext(Prefab.Configs configs) {
        loadConfigs(configs, Source.STREAMING);
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
        try {
          Thread.sleep(10000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        startStreaming();
      }
    });
  }

  private void loadConfigs(Prefab.Configs configs, Source source) {
    LOG.info("Load {} configs from {} in env {}", configs.getConfigsCount(), source, configs.getConfigServicePointer().getProjectEnvId());
    resolver.setProjectEnvId(configs.getConfigServicePointer().getProjectEnvId());

    for (Prefab.Config config : configs.getConfigsList()) {
      configLoader.set(config);
    }
    resolver.update();
    LOG.info("Loaded {} at highwater {} ", source, configLoader.getHighwaterMark());

    if (initializedLatch.getCount() > 0) {
      LOG.info("Initialized Prefab from {} at highwater {}", source, configLoader.getHighwaterMark());
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
