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
import cloud.prefab.client.config.Provenance;
import cloud.prefab.client.config.UpdatingConfigResolver;
import cloud.prefab.client.config.logging.AbstractLoggingListener;
import cloud.prefab.client.value.LiveBoolean;
import cloud.prefab.client.value.LiveDouble;
import cloud.prefab.client.value.LiveLong;
import cloud.prefab.client.value.LiveString;
import cloud.prefab.client.value.Value;
import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.GreetingServiceGrpc;
import cloud.prefab.domain.LoggerReportingServiceGrpc;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannelProvider;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigClientImpl implements ConfigClient {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigClientImpl.class);
  private static final String AUTH_USER = "authuser";
  private static final long DEFAULT_CHECKPOINT_SEC = 60;

  private static final long DEFAULT_LOG_STATS_UPLOAD_SEC = TimeUnit.MINUTES.toSeconds(5);
  private static final long INITIAL_LOG_STATS_UPLOAD_SEC = TimeUnit.MINUTES.toSeconds(1);

  private static final long BACKOFF_MILLIS = 10000;

  private static final String LOG_LEVEL_PREFIX_WITH_DOT =
    AbstractLoggingListener.LOG_LEVEL_PREFIX + ".";

  private final PrefabCloudClient baseClient;
  private final Options options;

  private final UpdatingConfigResolver updatingConfigResolver;
  private final ConfigLoader configLoader;

  private final CountDownLatch initializedLatch = new CountDownLatch(1);
  private final Set<ConfigChangeListener> configChangeListeners = Sets.newConcurrentHashSet();

  private final LoggerStatsAggregator loggerStatsAggregator;

  private final String uniqueClientId;

  private final Optional<Prefab.ConfigValue> namespaceMaybe;

  private final ConcurrentHashMap<String, String> loggerNameLookup = new ConcurrentHashMap<>();

  private final PrefabHttpClient prefabHttpClient;

  private final AtomicBoolean grpcAvailable = new AtomicBoolean(false);

  @Override
  public ConfigResolver getResolver() {
    return updatingConfigResolver.getResolver();
  }

  public ConfigClientImpl(
    PrefabCloudClient baseClient,
    ConfigChangeListener... listeners
  ) {
    this.uniqueClientId = UUID.randomUUID().toString();
    this.baseClient = baseClient;
    this.options = baseClient.getOptions();
    configLoader = new ConfigLoader(options);
    updatingConfigResolver = new UpdatingConfigResolver(baseClient, configLoader);
    configChangeListeners.add(
      new LoggingConfigListener(() -> initializedLatch.getCount() == 0)
    );
    configChangeListeners.addAll(Arrays.asList(listeners));
    namespaceMaybe =
      baseClient
        .getOptions()
        .getNamespace()
        .map(ns -> Prefab.ConfigValue.newBuilder().setString(ns).build());

    if (options.isLocalOnly() || !options.isReportLogStats()) {
      loggerStatsAggregator = null;
    } else {
      loggerStatsAggregator = new LoggerStatsAggregator(Clock.systemUTC());
      loggerStatsAggregator.start();
      startLogStatsUploadExecutor();
    }

    HttpClient httpClient;
    ConnectivityTester connectivityTester;
    if (options.isLocalOnly()) {
      finishInit(Source.LOCAL_ONLY);
      httpClient = null;
      connectivityTester = null;
      prefabHttpClient = null;
    } else {
      httpClient =
        HttpClient
          .newBuilder()
          .executor(
            MoreExecutors.getExitingExecutorService(
              (ThreadPoolExecutor) Executors.newCachedThreadPool(
                new ThreadFactoryBuilder()
                  .setDaemon(true)
                  .setNameFormat("prefab-http-client-pooled-thread-%d")
                  .build()
              )
            )
          )
          .build();

      connectivityTester =
        new ConnectivityTester(this::greetingServiceFutureStub, httpClient, options);
      connectivityTester.testHttps();
      grpcAvailable.set(options.isGrpcEnabled() && connectivityTester.testGrpc());
      prefabHttpClient = new PrefabHttpClient(httpClient, options);
      startStreaming();
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
    return get(key, Collections.emptyMap());
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
      if (!updatingConfigResolver.containsKey(key)) {
        return Optional.empty();
      }
      HashMap<String, Prefab.ConfigValue> mutableProperties = Maps.newHashMapWithExpectedSize(
        properties.size() + (namespaceMaybe.isPresent() ? 1 : 0)
      );
      mutableProperties.putAll(properties);
      namespaceMaybe.ifPresent(namespace ->
        mutableProperties.put(NAMESPACE_KEY, namespace)
      );

      return updatingConfigResolver.getConfigValue(key, mutableProperties);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
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

  @Override
  public void reportLoggerUsage(String loggerName, Prefab.LogLevel logLevel, long count) {
    if (logLevel != null && loggerStatsAggregator != null) {
      loggerStatsAggregator.reportLoggerUsage(loggerName, logLevel, count);
    }
  }

  @Override
  public Optional<Prefab.LogLevel> getLogLevel(
    String loggerName,
    Map<String, Prefab.ConfigValue> properties
  ) {
    for (Iterator<String> it = loggerNameLookupIterator(loggerName); it.hasNext();) {
      String configKey = it.next();
      Optional<Prefab.LogLevel> logLevelMaybe = get(configKey, properties)
        .filter(Prefab.ConfigValue::hasLogLevel)
        .map(Prefab.ConfigValue::getLogLevel);
      if (logLevelMaybe.isPresent()) {
        return logLevelMaybe;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<Prefab.LogLevel> getLogLevelFromStringMap(
    String loggerName,
    Map<String, String> properties
  ) {
    Map<String, Prefab.ConfigValue> map;

    if (properties.isEmpty()) {
      map = Collections.emptyMap();
    } else {
      ImmutableMap.Builder<String, Prefab.ConfigValue> mapBuilder = ImmutableMap.builder();
      for (Map.Entry<String, String> entry : properties.entrySet()) {
        mapBuilder.put(
          entry.getKey(),
          Prefab.ConfigValue.newBuilder().setString(entry.getValue()).build()
        );
      }
      map = mapBuilder.build();
    }
    return getLogLevel(loggerName, map);
  }

  @Override
  public boolean isReady() {
    return initializedLatch.getCount() == 0;
  }

  private Iterator<String> loggerNameLookupIterator(String loggerName) {
    return new Iterator<>() {
      String nextValue = LOG_LEVEL_PREFIX_WITH_DOT + loggerName;

      @Override
      public boolean hasNext() {
        return nextValue != null;
      }

      @Override
      public String next() {
        if (nextValue == null) {
          throw new NoSuchElementException();
        }
        String currentValue = nextValue;
        nextValue =
          loggerNameLookup.computeIfAbsent(
            currentValue,
            k -> {
              int lastDotIndex = nextValue.lastIndexOf('.');
              if (lastDotIndex > 0) {
                return nextValue.substring(0, lastDotIndex);
              } else {
                return null;
              }
            }
          );
        return currentValue;
      }
    };
  }

  private void loadCheckpoint() {
    boolean cdnSuccess = loadCDN();
    if (cdnSuccess) {
      return;
    }

    if (grpcAvailable.get()) {
      Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer
        .newBuilder()
        .setStartAtId(configLoader.getHighwaterMark())
        .build();

      loadAllConfigsViaGrpc(pointer);
    } else {
      LOG.debug("No fallback from CDN is available now w/o GRPC");
    }
  }

  private void loadAllConfigsViaGrpc(Prefab.ConfigServicePointer pointer) {
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
    LOG.debug("Loading {} from  {}", source, url);
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
      LOG.warn(
        "Unexpected issue with loading {} via {} (stack trace available at DEBUG)",
        source,
        url
      );
      LOG.debug("Unexpected issue with loading {} via {}", source, url, e);
    }
    return false;
  }

  private ScheduledExecutorService startStreamingExecutor() {
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(
      1,
      r -> new Thread(r, "prefab-streaming-callback-executor")
    );

    return MoreExecutors.getExitingScheduledExecutorService(
      (ScheduledThreadPoolExecutor) executor,
      100,
      TimeUnit.MILLISECONDS
    );
  }

  private void startStreaming() {
    ScheduledExecutorService scheduledExecutorService = startStreamingExecutor();
    if (grpcAvailable.get()) {
      LOG.info("Starting GRPC config subscriber");
      GrpcConfigStreamingSubscriber grpcConfigStreamingSubscriber = new GrpcConfigStreamingSubscriber(
        () -> baseClient.getChannel(),
        () -> configLoader.getHighwaterMark(),
        configs -> loadConfigs(configs, Source.STREAMING),
        scheduledExecutorService
      );
      grpcConfigStreamingSubscriber.start();
    } else {
      LOG.info("Starting SSE config subscriber");
      SseConfigStreamingSubscriber sseConfigStreamingSubscriber = new SseConfigStreamingSubscriber(
        prefabHttpClient,
        () -> configLoader.getHighwaterMark(),
        configs -> loadConfigs(configs, Source.STREAMING),
        scheduledExecutorService
      );
      sseConfigStreamingSubscriber.start();
    }
  }

  private void startLogStatsUploadExecutor() {
    LOG.info("Starting log stats uploader");
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(
      1,
      r -> new Thread(r, "prefab-logger-stats-uploader")
    );

    ScheduledExecutorService scheduledExecutorService = MoreExecutors.getExitingScheduledExecutorService(
      (ScheduledThreadPoolExecutor) executor,
      100,
      TimeUnit.MILLISECONDS
    );

    scheduledExecutorService.scheduleAtFixedRate(
      () -> {
        // allowing an exception to reach the ScheduledExecutor cancels future execution
        // To prevent that we need to catch and log here
        try {
          long now = System.currentTimeMillis();
          LoggerStatsAggregator.LogCounts logCounts = loggerStatsAggregator.getAndResetStats();

          Prefab.Loggers.Builder loggersBuilder = Prefab.Loggers
            .newBuilder()
            .setStartAt(logCounts.getStartTime())
            .setEndAt(now)
            .addAllLoggers(logCounts.getLoggerMap().values())
            .setInstanceHash(uniqueClientId);
          options.getNamespace().ifPresent(loggersBuilder::setNamespace);

          if (grpcAvailable.get()) {
            LOG.info(
              "Uploading stats for {} loggers via GRPC",
              logCounts.getLoggerMap().size()
            );
            loggerReportingServiceStub()
              .send(
                loggersBuilder.build(),
                new StreamObserver<>() {
                  @Override
                  public void onNext(Prefab.LoggerReportResponse loggerReportResponse) {}

                  @Override
                  public void onError(Throwable throwable) {
                    LOG.warn("log stats upload failed", throwable);
                  }

                  @Override
                  public void onCompleted() {
                    LOG.debug("log stats upload completed");
                  }
                }
              );
          } else {
            LOG.info(
              "Uploading stats for {} loggers via HTTP",
              logCounts.getLoggerMap().size()
            );
            prefabHttpClient.reportLoggers(loggersBuilder.build());
          }
        } catch (Exception e) {
          LOG.warn("Error setting up aggregated log stats transmission", e);
        }
      },
      INITIAL_LOG_STATS_UPLOAD_SEC,
      DEFAULT_LOG_STATS_UPLOAD_SEC,
      TimeUnit.SECONDS
    );
  }

  private void startCheckpointExecutor() {
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(
      1,
      r -> new Thread(r, "prefab-logger-checkpoint-executor")
    );

    ScheduledExecutorService scheduledExecutorService = MoreExecutors.getExitingScheduledExecutorService(
      (ScheduledThreadPoolExecutor) executor,
      100,
      TimeUnit.MILLISECONDS
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
      initializedLatch.countDown();
      LOG.info(
        "Initialized Prefab from {} at highwater {} with currently known configs\n {}",
        source,
        configLoader.getHighwaterMark(),
        updatingConfigResolver.contentsString()
      );
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
      configLoader.set(new ConfigElement(config, new Provenance(source)));
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

  private LoggerReportingServiceGrpc.LoggerReportingServiceStub loggerReportingServiceStub() {
    return LoggerReportingServiceGrpc.newStub(baseClient.getChannel());
  }

  private GreetingServiceGrpc.GreetingServiceFutureStub greetingServiceFutureStub() {
    return GreetingServiceGrpc.newFutureStub(baseClient.getChannel());
  }
}
