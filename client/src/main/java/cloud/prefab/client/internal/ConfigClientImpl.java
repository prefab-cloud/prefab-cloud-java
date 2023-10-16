package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.PrefabInitializationTimeoutException;
import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.logging.AbstractLoggingListener;
import cloud.prefab.client.value.LiveBoolean;
import cloud.prefab.client.value.LiveDouble;
import cloud.prefab.client.value.LiveLong;
import cloud.prefab.client.value.LiveString;
import cloud.prefab.client.value.LiveStringList;
import cloud.prefab.client.value.Value;
import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigClientImpl implements ConfigClient {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigClientImpl.class);
  private static final String AUTH_USER = "authuser";
  private static final long DEFAULT_CHECKPOINT_SEC = 60;

  private static final long DEFAULT_LOG_STATS_UPLOAD_SEC = TimeUnit.MINUTES.toSeconds(5);
  private static final long INITIAL_LOG_STATS_UPLOAD_SEC = TimeUnit.MINUTES.toSeconds(1);

  private static final String LOG_LEVEL_PREFIX_WITH_DOT =
    AbstractLoggingListener.LOG_LEVEL_PREFIX + ".";

  private final Options options;

  private final UpdatingConfigResolver updatingConfigResolver;

  private final CountDownLatch initializedLatch = new CountDownLatch(1);
  private final Set<ConfigChangeListener> configChangeListeners = Sets.newConcurrentHashSet();

  private final LoggerStatsAggregator loggerStatsAggregator;

  private final String uniqueClientId;

  private final Optional<Prefab.ConfigValue> namespaceMaybe;

  private final ConcurrentHashMap<String, String> loggerNameLookup = new ConcurrentHashMap<>();

  private final PrefabHttpClient prefabHttpClient;

  private final ContextStore contextStore;
  private final ContextResolver contextResolver;
  private MatchProcessingManager matchProcessingManager;

  private ContextShapeAggregator contextShapeAggregator = null;

  public ConfigClientImpl(
    PrefabCloudClient baseClient,
    ConfigChangeListener... listeners
  ) {
    this(
      baseClient,
      new UpdatingConfigResolver(
        new ConfigLoader(baseClient.getOptions()),
        new WeightedValueEvaluator(),
        new ConfigStoreDeltaCalculator()
      ),
      listeners
    );
  }

  @VisibleForTesting
  ConfigClientImpl(
    PrefabCloudClient baseClient,
    UpdatingConfigResolver updatingConfigResolver,
    ConfigChangeListener... listeners
  ) {
    this.uniqueClientId = UUID.randomUUID().toString();
    this.options = baseClient.getOptions();
    this.updatingConfigResolver = updatingConfigResolver;
    this.contextResolver =
      new ContextResolver(updatingConfigResolver::getDefaultContext, getContextStore());
    configChangeListeners.add(
      new LoggingConfigListener(() -> initializedLatch.getCount() == 0)
    );
    configChangeListeners.addAll(baseClient.getOptions().getChangeListeners());
    configChangeListeners.addAll(Arrays.asList(listeners));
    namespaceMaybe =
      baseClient
        .getOptions()
        .getNamespace()
        .map(ns -> Prefab.ConfigValue.newBuilder().setString(ns).build());

    contextStore = options.getContextStore();
    if (options.isLocalOnly() || !options.isCollectLoggerCounts()) {
      loggerStatsAggregator = null;
    } else {
      loggerStatsAggregator = new LoggerStatsAggregator(Clock.systemUTC());
      loggerStatsAggregator.start();
      startLogStatsUploadExecutor();
    }

    if (options.isLocalOnly()) {
      finishInit(Source.LOCAL_ONLY);
      prefabHttpClient = null;
    } else {
      HttpClient httpClient = HttpClient
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
      ConnectivityTester connectivityTester = new ConnectivityTester(httpClient, options);
      connectivityTester.testHttps();
      prefabHttpClient = new PrefabHttpClient(httpClient, options);
      startStreaming();
      startCheckpointExecutor();
      if (options.isCollectContextShapeEnabled()) {
        contextShapeAggregator =
          new ContextShapeAggregator(options, prefabHttpClient, Clock.systemUTC());
        contextShapeAggregator.start();
      }
      if (
        options.isCollectEvaluationSummaries() || options.isCollectExampleContextEnabled()
      ) {
        matchProcessingManager = new MatchProcessingManager(prefabHttpClient, options);
        matchProcessingManager.start();
      }
    }
  }

  @Override
  public Value<String> liveString(String key) {
    return new LiveString(this, key);
  }

  @Override
  public Value<List<String>> liveStringList(String key) {
    return new LiveStringList(this, key);
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
    return get(key, (PrefabContextSetReadable) null);
  }

  @Override
  public Optional<Prefab.ConfigValue> get(
    String configKey,
    Map<String, Prefab.ConfigValue> properties
  ) {
    return get(configKey, PrefabContext.unnamedFromMap(properties));
  }

  @Override
  public Optional<Prefab.ConfigValue> get(
    String configKey,
    @Nullable PrefabContextSetReadable prefabContext
  ) {
    PrefabContextSetReadable resolvedContext = resolveContext(prefabContext);
    reportUsage(configKey, resolvedContext);
    return getInternal(configKey, contextResolver.resolve(prefabContext));
  }

  private void reportUsage(String configKey, PrefabContextSetReadable prefabContext) {
    if (contextShapeAggregator != null) {
      contextShapeAggregator.reportContextUsage(prefabContext);
    }
  }

  @Override
  public Map<String, Prefab.ConfigValue> getAll(
    @Nullable PrefabContextSetReadable prefabContext
  ) {
    LookupContext lookupContext = new LookupContext(
      namespaceMaybe,
      resolveContext(prefabContext)
    );
    ImmutableMap.Builder<String, Prefab.ConfigValue> bldr = ImmutableMap.builder();
    for (String key : getAllKeys()) {
      updatingConfigResolver
        .getConfigValue(key, lookupContext)
        .ifPresent(configValue -> bldr.put(key, configValue));
    }
    return bldr.build();
  }

  @Override
  public Collection<String> getAllKeys() {
    return updatingConfigResolver.getResolver().getKeys();
  }

  private Optional<Prefab.ConfigValue> getInternal(
    String configKey,
    LookupContext lookupContext
  ) {
    waitForInitialization();
    Optional<Match> matchMaybe = getMatchInternal(configKey, lookupContext);

    matchMaybe.ifPresent(match -> reportMatchResult(match, lookupContext));

    return matchMaybe.map(Match::getConfigValue);
  }

  private void reportMatchResult(Match match, LookupContext lookupContext) {
    if (matchProcessingManager != null) {
      matchProcessingManager.reportMatch(match, lookupContext);
    }
  }

  private Optional<Match> getMatchInternal(
    String configKey,
    LookupContext lookupContext
  ) {
    waitForInitialization();
    return updatingConfigResolver.getMatch(configKey, lookupContext);
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
  public Optional<Prefab.LogLevel> getLogLevel(String loggerName) {
    return getLogLevel(loggerName, null);
  }

  @Override
  public Optional<Prefab.LogLevel> getLogLevel(
    String loggerName,
    @Nullable PrefabContextSetReadable prefabContext
  ) {
    LookupContext lookupContext = new LookupContext(
      namespaceMaybe,
      resolveContext(prefabContext)
    );
    for (Iterator<String> it = loggerNameLookupIterator(loggerName); it.hasNext();) {
      String configKey = it.next();
      Optional<Prefab.LogLevel> logLevelMaybe = getInternal(configKey, lookupContext)
        .filter(Prefab.ConfigValue::hasLogLevel)
        .map(Prefab.ConfigValue::getLogLevel);
      if (logLevelMaybe.isPresent()) {
        return logLevelMaybe;
      }
    }
    return Optional.empty();
  }

  private PrefabContextSetReadable resolveContext(
    @Nullable PrefabContextSetReadable prefabContextSetReadable
  ) {
    Optional<PrefabContextSetReadable> newContext = Optional
      .ofNullable(prefabContextSetReadable)
      .filter(Predicate.not(PrefabContextSetReadable::isEmpty));

    Optional<PrefabContextSetReadable> existingContext = getContextStore()
      .getContext()
      .filter(Predicate.not(PrefabContextSetReadable::isEmpty));

    if (LOG.isTraceEnabled()) {
      LOG.trace(
        "context store contains {}, after filtering {}",
        getContextStore().getContext().map(Object::toString).orElse("n/a"),
        existingContext.map(Object::toString).orElse("n/a")
      );

      LOG.trace(
        "Merging passed context {} with context store {}",
        prefabContextSetReadable,
        getContextStore().getContext().map(Object::toString).orElse("n/a")
      );
    }

    if (newContext.isEmpty()) {
      return existingContext.orElse(PrefabContextSetReadable.EMPTY);
    } else {
      if (existingContext.isEmpty()) {
        return newContext.get();
      } else {
        // do the merge
        PrefabContextSet prefabContextSet = new PrefabContextSet();
        for (PrefabContext context : existingContext.get().getContexts()) {
          prefabContextSet.addContext(context);
        }
        for (PrefabContext context : newContext.get().getContexts()) {
          prefabContextSet.addContext(context);
        }
        return prefabContextSet;
      }
    }
  }

  @Override
  public boolean isReady() {
    return initializedLatch.getCount() == 0;
  }

  @Override
  public ContextStore getContextStore() {
    return contextStore;
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
        String temp = loggerNameLookup.get(currentValue);
        if (temp == null) {
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
        } else {
          nextValue = temp;
        }
        return currentValue;
      }
    };
  }

  private void loadCheckpoint() {
    boolean cdnSuccess = loadCDN();
    if (cdnSuccess) {
      return;
    }
    loadAPI();
  }

  boolean loadCDN() {
    try {
      HttpResponse<Supplier<Prefab.Configs>> response = prefabHttpClient
        .requestConfigsFromCDN(0)
        .get(5, TimeUnit.SECONDS);
      if (PrefabHttpClient.isSuccess(response.statusCode())) {
        loadConfigs(response.body().get(), Source.REMOTE_CDN);
        return true;
      }
      LOG.info(
        "Got {} loading configs from CDN url {}",
        response.statusCode(),
        response.request().uri()
      );
    } catch (Exception e) {
      LOG.info("Got exception with message {} loading configs from CDN", e.getMessage());
    }
    return false;
  }

  boolean loadAPI() {
    try {
      HttpResponse<Supplier<Prefab.Configs>> response = prefabHttpClient
        .requestConfigsFromApi(0)
        .get(5, TimeUnit.SECONDS);
      if (PrefabHttpClient.isSuccess(response.statusCode())) {
        loadConfigs(response.body().get(), Source.REMOTE_API);
        return true;
      }
      LOG.info(
        "Got {} loading configs from API url {}",
        response.statusCode(),
        response.request().uri()
      );
    } catch (Exception e) {
      LOG.info("Got exception with message {} loading configs from API", e.getMessage());
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

    LOG.info("Starting SSE config subscriber");
    SseConfigStreamingSubscriber sseConfigStreamingSubscriber = new SseConfigStreamingSubscriber(
      prefabHttpClient,
      updatingConfigResolver::getHighwaterMark,
      configs -> loadConfigs(configs, Source.STREAMING),
      scheduledExecutorService
    );
    sseConfigStreamingSubscriber.start();
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

          LOG.info(
            "Uploading stats for {} loggers via HTTP",
            logCounts.getLoggerMap().size()
          );
          prefabHttpClient.reportLoggers(loggersBuilder.build());
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
        updatingConfigResolver.getHighwaterMark(),
        updatingConfigResolver.contentsString()
      );
    }
  }

  private synchronized void loadConfigs(Prefab.Configs configs, Source source) {
    LOG.debug(
      "Loading {} configs from {} pointer {}",
      configs.getConfigsCount(),
      source,
      configs.hasConfigServicePointer()
    );
    updatingConfigResolver.loadConfigs(configs, source);

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

  private void waitForInitialization() {
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
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
