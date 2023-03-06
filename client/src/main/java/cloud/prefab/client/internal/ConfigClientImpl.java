package cloud.prefab.client.internal;

import static cloud.prefab.client.config.ConfigResolver.NAMESPACE_KEY;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.PrefabInitializationTimeoutException;
import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.ConfigLoader;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.client.config.LoggingConfigListener;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.client.config.UpdatingConfigResolver;
import cloud.prefab.client.value.LiveBoolean;
import cloud.prefab.client.value.LiveDouble;
import cloud.prefab.client.value.LiveLong;
import cloud.prefab.client.value.LiveString;
import cloud.prefab.client.value.Value;
import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigClientImpl implements ConfigClient {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigClientImpl.class);
  private static final String AUTH_USER = "authuser";
  private static final long DEFAULT_CHECKPOINT_SEC = 60;
  private static final long BACKOFF_MILLIS = 3000;

  private final PrefabCloudClient baseClient;
  private final Options options;

  private final UpdatingConfigResolver updatingConfigResolver;
  private final ConfigLoader configLoader;

  private final CountDownLatch initializedLatch = new CountDownLatch(1);
  private final Set<ConfigChangeListener> configChangeListeners = Sets.newConcurrentHashSet();

  @Override
  public ConfigResolver getResolver() {
    return updatingConfigResolver.getResolver();
  }

  public ConfigClientImpl(
    PrefabCloudClient baseClient,
    ConfigChangeListener... listeners
  ) {
    this.baseClient = baseClient;
    this.options = baseClient.getOptions();
    configLoader = new ConfigLoader(options);
    updatingConfigResolver = new UpdatingConfigResolver(baseClient, configLoader);
    configChangeListeners.add(LoggingConfigListener.getInstance());
    configChangeListeners.addAll(Arrays.asList(listeners));

    if (options.isLocalOnly()) {
      finishInit(Source.LOCAL_ONLY);
    } else {
      startStreamingExecutor();
      startCheckpointExecutor();
    }
  }

  @Override
  public Value<String> liveString(String key) {
    return new LiveString(this, key);
  }

  @Override
  public Value<Long> liveLong(String key) {
    return new LiveLong(this, key);
  }

  @Override
  public Value<Double> liveDouble(String key) {
    return new LiveDouble(this, key);
  }

  @Override
  public Value<Boolean> liveBoolean(String key) {
    return new LiveBoolean(this, key);
  }

  @Override
  public Optional<Prefab.ConfigValue> get(String key) {
    return get(key, new HashMap<>());
  }

  @Override
  public Optional<Prefab.ConfigValue> get(
    String key,
    Map<String, Prefab.ConfigValue> properties
  ) {
    try {
      if (
        !initializedLatch.await(options.getInitializationTimeoutSec(), TimeUnit.SECONDS)
      ) {
        if (
          options.getOnInitializationFailure() == Options.OnInitializationFailure.UNLOCK
        ) {
          finishInit(Source.INIT_TIMEOUT);
        } else {
          throw new PrefabInitializationTimeoutException(
            options.getInitializationTimeoutSec()
          );
        }
      }
      if (baseClient.getOptions().getNamespace().isPresent()) {
        properties.put(
          NAMESPACE_KEY,
          Prefab.ConfigValue
            .newBuilder()
            .setString(baseClient.getOptions().getNamespace().get())
            .build()
        );
      }
      return updatingConfigResolver.getConfigValue(key, properties);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public Map<String, Prefab.ConfigValue> getAllValues() {
    return getResolver().getAllValues();
  }

  @Override
  public void upsert(String key, Prefab.ConfigValue configValue) {
    Prefab.Config upsertRequest = Prefab.Config
      .newBuilder()
      .setKey(key)
      .addRows(
        Prefab.ConfigRow
          .newBuilder()
          .addValues(Prefab.ConditionalValue.newBuilder().setValue(configValue).build())
      )
      .build();

    configServiceBlockingStub().upsert(upsertRequest);
  }

  @Override
  public void upsert(Prefab.Config config) {
    configServiceBlockingStub().upsert(config);
  }

  @Override
  public boolean addConfigChangeListener(ConfigChangeListener configChangeListener) {
    return configChangeListeners.add(configChangeListener);
  }

  @Override
  public boolean removeConfigChangeListener(ConfigChangeListener configChangeListener) {
    return configChangeListeners.remove(configChangeListener);
  }

  private void loadCheckpoint() {
    boolean cdnSuccess = loadCDN();
    if (cdnSuccess) {
      return;
    }

    Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer
      .newBuilder()
      .setStartAtId(configLoader.getHighwaterMark())
      .build();

    loadGrpc(pointer);
  }

  private void loadGrpc(Prefab.ConfigServicePointer pointer) {
    configServiceStub()
      .getAllConfig(
        pointer,
        new StreamObserver<>() {
          @Override
          public void onNext(Prefab.Configs configs) {
            loadConfigs(configs, Source.REMOTE_API_GRPC);
          }

          @Override
          public void onError(Throwable throwable) {
            LOG.warn(
              "{} Issue getting checkpoint config",
              Source.REMOTE_API_GRPC,
              throwable
            );
          }

          @Override
          public void onCompleted() {}
        }
      );
  }

  boolean loadCDN() {
    final String url = String.format("%s/api/v1/configs/0", options.getCDNUrl());
    return loadCheckpointFromUrl(url, Source.REMOTE_CDN);
  }

  private static String getBasicAuthenticationHeader(String username, String password) {
    String valueToEncode = username + ":" + password;
    return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
  }

  private boolean loadCheckpointFromUrl(String url, Source source) {
    LOG.debug("Loading from {} {}", url, source);
    try {
      HttpRequest request = HttpRequest
        .newBuilder()
        .GET()
        .uri(new URI(url))
        .header(
          "Authorization",
          getBasicAuthenticationHeader(AUTH_USER, options.getApikey())
        )
        .build();

      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<byte[]> response = client.send(
        request,
        HttpResponse.BodyHandlers.ofByteArray()
      );

      if (response.statusCode() != 200) {
        LOG.warn("Problem loading {} {} {}", source, response.statusCode(), url);
      } else {
        Prefab.Configs configs = Prefab.Configs.parseFrom(response.body());
        loadConfigs(configs, source);
        return true;
      }
    } catch (Exception e) {
      LOG.warn("Unexpected issue with CDN load {}", e.getMessage());
    }
    return false;
  }

  private void startStreamingExecutor() {
    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

    ExecutorService executorService = MoreExecutors.getExitingExecutorService(
      executor,
      100,
      TimeUnit.MILLISECONDS
    );
    executorService.execute(() -> startStreaming());
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
        new StreamObserver<>() {
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
              LOG.warn("Error from streaming API will restart streaming connection");
              LOG.debug("Details of streaming API failure are", throwable);
              try {
                Thread.sleep(BACKOFF_MILLIS);
              } catch (InterruptedException e) {
                LOG.warn("Interruption Backing Off");
                Thread.currentThread().interrupt();
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

  private void startCheckpointExecutor() {
    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(
      1
    );
    scheduledExecutorService.scheduleAtFixedRate(
      () -> {
        // allowing an exception to reach the ScheduledExecutor cancels future execution
        // To prevent that we need to catch and log here
        try {
          loadCheckpoint();
        } catch (Exception e) {
          LOG.warn("Error getting checkpoint - will try again", e);
        }
      },
      0,
      DEFAULT_CHECKPOINT_SEC,
      TimeUnit.SECONDS
    );
  }

  private void finishInit(Source source) {
    final List<ConfigChangeEvent> changes = updatingConfigResolver.update();
    broadcastChanges(changes);
    if (initializedLatch.getCount() > 0) {
      LOG.info(
        "Initialized Prefab from {} at highwater {}",
        source,
        configLoader.getHighwaterMark()
      );
      LOG.info(updatingConfigResolver.contentsString());
      initializedLatch.countDown();
    }
  }

  private void loadConfigs(Prefab.Configs configs, Source source) {
    LOG.debug(
      "Loading {} configs from {} pointer {}",
      configs.getConfigsCount(),
      source,
      configs.hasConfigServicePointer()
    );
    updatingConfigResolver.setProjectEnvId(configs);

    final long startingHighWaterMark = configLoader.getHighwaterMark();

    for (Prefab.Config config : configs.getConfigsList()) {
      configLoader.set(new ConfigElement(config, new Provenance(source, "")));
    }

    if (configLoader.getHighwaterMark() > startingHighWaterMark) {
      LOG.info(
        "Found new checkpoint with highwater id {} from {} in project {} environment: {} and namespace: '{}' with {} configs",
        configLoader.getHighwaterMark(),
        source,
        configs.getConfigServicePointer().getProjectId(),
        configs.getConfigServicePointer().getProjectEnvId(),
        options.getNamespace(),
        configs.getConfigsCount()
      );
    } else {
      LOG.debug(
        "Checkpoint with highwater with highwater id {} from {}. No changes.",
        configLoader.getHighwaterMark(),
        source
      );
    }

    finishInit(source);
  }

  private void broadcastChanges(List<ConfigChangeEvent> changeEvents) {
    List<ConfigChangeListener> listeners = new ArrayList<>(configChangeListeners);

    for (ConfigChangeListener listener : listeners) {
      for (ConfigChangeEvent changeEvent : changeEvents) {
        LOG.debug("Broadcasting change {} to {}", changeEvent, listener);
        listener.onChange(changeEvent);
      }
    }
  }

  private ConfigServiceGrpc.ConfigServiceBlockingStub configServiceBlockingStub() {
    return ConfigServiceGrpc.newBlockingStub(baseClient.getChannel());
  }

  private ConfigServiceGrpc.ConfigServiceStub configServiceStub() {
    return ConfigServiceGrpc.newStub(baseClient.getChannel());
  }
}
