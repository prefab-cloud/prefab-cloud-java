package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.PrefabInitializationTimeoutException;
import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.logging.AbstractLoggingListener;
import cloud.prefab.client.config.logging.LogLevelChangeEvent;
import cloud.prefab.client.config.logging.LogLevelChangeListener;
import cloud.prefab.client.value.LiveBoolean;
import cloud.prefab.client.value.LiveDouble;
import cloud.prefab.client.value.LiveDuration;
import cloud.prefab.client.value.LiveLong;
import cloud.prefab.client.value.LiveString;
import cloud.prefab.client.value.LiveStringList;
import cloud.prefab.client.value.Value;
import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
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
import java.time.Duration;
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
  private static final long DEFAULT_CHECKPOINT_SEC = 60;

  private static final String LOG_LEVEL_PREFIX_WITH_DOT =
    AbstractLoggingListener.LOG_LEVEL_PREFIX + ".";

  private final Options options;

  private final UpdatingConfigResolver updatingConfigResolver;

  private final CountDownLatch initializedLatch = new CountDownLatch(1);
  private final Set<ConfigChangeListener> configChangeListeners = Sets.newConcurrentHashSet();

  private final Set<LogLevelChangeListener> logLevelChangeListeners = Sets.newConcurrentHashSet();

  private final String uniqueClientId;

  private final ConcurrentHashMap<String, String> loggerNameLookup = new ConcurrentHashMap<>();

  private final PrefabHttpClient prefabHttpClient;

  private final ContextStore contextStore;
  private final TelemetryManager telemetryManager;
  private final TypedConfigClientImpl typedConfigImpl;

  public ConfigClientImpl(
    PrefabCloudClient baseClient,
    ConfigChangeListener... listeners
  ) {
    this(
      baseClient,
      new UpdatingConfigResolver(
        new ConfigLoader(baseClient.getOptions()),
        new WeightedValueEvaluator(),
        new ConfigStoreConfigValueDeltaCalculator()
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
    configChangeListeners.add(
      new LoggingConfigListener(() -> initializedLatch.getCount() == 0)
    );
    configChangeListeners.addAll(baseClient.getOptions().getChangeListeners());
    configChangeListeners.addAll(Arrays.asList(listeners));
    logLevelChangeListeners.addAll(baseClient.getOptions().getLogLevelChangeListeners());
    contextStore = options.getContextStore();
    typedConfigImpl = new TypedConfigClientImpl(this);
    if (options.isLocalOnly()) {
      finishInit(Source.LOCAL_ONLY);
      prefabHttpClient = null;
      telemetryManager = null;
    } else if (options.isLocalDatafileMode()) {
      updatingConfigResolver.loadConfigsFromLocalFile();
      finishInit(Source.LOCAL_FILE);
      prefabHttpClient = null;
      telemetryManager = null;
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
      Executors.newSingleThreadExecutor().submit(this::startConnections);
      telemetryManager =
        new TelemetryManager(
          new LoggerStatsAggregator(Clock.systemUTC()),
          new MatchStatsAggregator(),
          new ContextShapeAggregator(),
          new ExampleContextBuffer(),
          prefabHttpClient,
          options,
          Clock.systemUTC()
        );
      telemetryManager.start();
    }
  }

  private void startConnections() {
    Optional<Prefab.Configs> configsMaybe = loadConfigs();
    configsMaybe.ifPresent(configs -> {
      long maxId = configs
        .getConfigsList()
        .stream()
        .mapToLong(Prefab.Config::getId)
        .max()
        .orElse(0);
      startStreaming(maxId);
    });
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
  public Value<Duration> liveDuration(String key) {
    return new LiveDuration(this, key);
  }

  @Override
  public Value<Boolean> liveBoolean(String key) {
    return new LiveBoolean(this, key);
  }

  @Override
  public boolean getBoolean(
    String key,
    boolean defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    return typedConfigImpl.getBoolean(key, defaultValue, context);
  }

  @Override
  public long getLong(
    String key,
    long defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    return typedConfigImpl.getLong(key, defaultValue, context);
  }

  @Override
  public double getDouble(
    String key,
    double defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    return typedConfigImpl.getDouble(key, defaultValue, context);
  }

  @Override
  public String getString(
    String key,
    String defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    return typedConfigImpl.getString(key, defaultValue, context);
  }

  @Override
  public List<String> getStringList(
    String key,
    List<String> defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    return typedConfigImpl.getStringList(key, defaultValue, context);
  }

  @Override
  public Duration getDuration(
    String key,
    Duration defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    return typedConfigImpl.getDuration(key, defaultValue, context);
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
    return getInternal(configKey, prefabContext);
  }

  @Override
  public Map<String, Prefab.ConfigValue> getAll(
    @Nullable PrefabContextSetReadable prefabContext
  ) {
    LookupContext lookupContext = new LookupContext(resolveContext(prefabContext));
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
    PrefabContextSetReadable passedContext
  ) {
    waitForInitialization();
    PrefabContextSetReadable resolvedContext = resolveContext(passedContext);
    LookupContext lookupContext = new LookupContext(resolvedContext);
    Optional<Match> matchMaybe = getMatchInternal(configKey, lookupContext);
    reportMatchResult(configKey, matchMaybe.orElse(null), lookupContext);
    return matchMaybe.map(Match::getConfigValue);
  }

  private Optional<Prefab.ConfigValue> getInternal(
    String configKey,
    LookupContext lookupContext
  ) {
    waitForInitialization();
    Optional<Match> matchMaybe = getMatchInternal(configKey, lookupContext);
    reportMatchResult(configKey, matchMaybe.orElse(null), lookupContext);
    return matchMaybe.map(Match::getConfigValue);
  }

  private void reportMatchResult(
    String configKey,
    @Nullable Match match,
    LookupContext lookupContext
  ) {
    if (telemetryManager != null) {
      telemetryManager.reportMatch(configKey, match, lookupContext);
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
    if (logLevel != null && telemetryManager != null) {
      telemetryManager.reportLoggerUsage(loggerName, logLevel, count);
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
    waitForInitialization();
    // special case getLogLevel - want to reuse the same lookup context for all key name variants
    // wait for initialization must happen before resolving the context so that any api-sent context
    // will be included
    LookupContext lookupContext = new LookupContext(resolveContext(prefabContext));
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
    return ContextMerger.merge(
      updatingConfigResolver.getGlobalContext(),
      updatingConfigResolver.getApiDefaultContext(),
      getContextStore()
        .getContext()
        .filter(Predicate.not(PrefabContextSetReadable::isEmpty))
        .orElse(PrefabContextSetReadable.EMPTY),
      Optional
        .ofNullable(prefabContextSetReadable)
        .filter(Predicate.not(PrefabContextSetReadable::isEmpty))
        .orElse(PrefabContextSetReadable.EMPTY)
    );
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

  Optional<Prefab.Configs> loadConfigs() {
    try {
      HttpResponse<Supplier<Prefab.Configs>> response = prefabHttpClient
        .requestConfigs(0)
        .get(5, TimeUnit.SECONDS);
      LOG.info(
        "Got {} loading configs from API url {}",
        response.statusCode(),
        response.request().uri()
      );

      if (PrefabHttpClient.isSuccess(response.statusCode())) {
        Prefab.Configs configs = response.body().get();
        loadConfigs(configs, Source.REMOTE_API);
        return Optional.of(configs);
      }
    } catch (Exception e) {
      LOG.info("Got exception with message {} loading configs from API", e.getMessage());
    }
    return Optional.empty();
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

  private void startStreaming(long highWaterMark) {
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

  private void finishInit(Source source) {
    final UpdatingConfigResolver.ChangeLists changes = updatingConfigResolver.update();
    broadcastChanges(changes.getConfigChangeEvents());
    broadcastLogLevelChanges(changes.getLogLevelChangeEvents());
    if (initializedLatch.getCount() > 0) {
      initializedLatch.countDown();
      try {
        LOG.info(
          "Initialized Prefab from {} at highwater {} with currently known configs\n{}",
          source,
          updatingConfigResolver.getHighwaterMark(),
          updatingConfigResolver.contentsString()
        );
      } catch (Exception e) {
        LOG.info("Error encountered printing known config listing", e);
      }
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
        try {
          listener.onChange(changeEvent);
        } catch (Exception e) {
          LOG.debug("Exception in config change listener", e);
        }
      }
    }
  }

  private void broadcastLogLevelChanges(List<LogLevelChangeEvent> changeEvents) {
    for (LogLevelChangeListener listener : logLevelChangeListeners) {
      for (LogLevelChangeEvent changeEvent : changeEvents) {
        LOG.debug("Broadcasting loglevel change {} to {}", changeEvent, listener);
        try {
          listener.onChange(changeEvent);
        } catch (Exception e) {
          LOG.debug("Exception in loglevel change listener", e);
        }
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
