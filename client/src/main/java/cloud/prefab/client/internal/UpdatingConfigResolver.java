package cloud.prefab.client.internal;

import static cloud.prefab.client.config.logging.AbstractLoggingListener.LOG_LEVEL_PREFIX;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.client.config.logging.LogLevelChangeEvent;
import cloud.prefab.client.exceptions.ConfigValueException;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdatingConfigResolver {

  private static final Logger LOG = LoggerFactory.getLogger(UpdatingConfigResolver.class);

  private final ConfigLoader configLoader;
  private final ConfigStoreConfigValueDeltaCalculator configStoreConfigValueDeltaCalculator;

  private final ConfigStoreImpl configStore;
  private final ConfigResolver configResolver;
  private final AbstractConfigStoreDeltaCalculator<Prefab.LogLevel, LogLevelChangeEvent> logLevelValueDeltaCalculator;

  public UpdatingConfigResolver(
    ConfigLoader configLoader,
    WeightedValueEvaluator weightedValueEvaluator,
    ConfigStoreConfigValueDeltaCalculator configStoreConfigValueDeltaCalculator
  ) {
    this.configLoader = configLoader;
    this.configStoreConfigValueDeltaCalculator = configStoreConfigValueDeltaCalculator;
    this.logLevelValueDeltaCalculator =
      new AbstractConfigStoreDeltaCalculator<>() {
        @Override
        LogLevelChangeEvent createEvent(
          String name,
          Optional<Prefab.LogLevel> oldValue,
          Optional<Prefab.LogLevel> newValue
        ) {
          return new LogLevelChangeEvent(name, oldValue, newValue);
        }
      };
    this.configStore = new ConfigStoreImpl();
    ConfigRuleEvaluator configRuleEvaluator = new ConfigRuleEvaluator(
      configStore,
      weightedValueEvaluator
    );
    this.configResolver =
      new ConfigResolver(configStore, configRuleEvaluator, new SystemEnvVarLookup());
  }

  /**
   * Return the changed config values since last update()
   */

  public static class ChangeLists {

    final List<ConfigChangeEvent> configChangeEvents;

    final List<LogLevelChangeEvent> logLevelChangeEvents;

    public ChangeLists(
      List<ConfigChangeEvent> configChangeEvents,
      List<LogLevelChangeEvent> logLevelChangeEvents
    ) {
      this.configChangeEvents = configChangeEvents;
      this.logLevelChangeEvents = logLevelChangeEvents;
    }

    public List<ConfigChangeEvent> getConfigChangeEvents() {
      return configChangeEvents;
    }

    public List<LogLevelChangeEvent> getLogLevelChangeEvents() {
      return logLevelChangeEvents;
    }
  }

  public ChangeLists update() {
    // catch exceptions resolving, treat as absent
    // store the old map
    Map<String, Prefab.Config> before = buildConfigByNameMap();
    Map<String, Prefab.LogLevel> logLevelsBefore = buildLogLevelValueMap();

    // load the new map
    makeLocal();

    // build the new map
    Map<String, Prefab.Config> after = buildConfigByNameMap();
    Map<String, Prefab.LogLevel> logLevelsAfter = buildLogLevelValueMap();

    return new ChangeLists(
      configStoreConfigValueDeltaCalculator.computeChangeEvents(before, after),
      logLevelValueDeltaCalculator.computeChangeEvents(logLevelsBefore, logLevelsAfter)
    );
  }

  private Map<String, Prefab.LogLevel> buildLogLevelValueMap() {
    return configStore
      .entrySet()
      .stream()
      .filter(stringConfigElementEntry ->
        stringConfigElementEntry.getValue().getConfig().getConfigType() ==
        Prefab.ConfigType.LOG_LEVEL ||
        stringConfigElementEntry.getKey().startsWith(LOG_LEVEL_PREFIX)
      )
      .map(entry ->
        safeResolve(entry.getKey())
          .map(cv -> Maps.immutableEntry(entry.getKey(), cv))
          .filter(resolvedEntry -> resolvedEntry.getValue().hasLogLevel())
      )
      .flatMap(Optional::stream)
      .collect(
        Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getLogLevel())
      );
  }

  private Map<String, Prefab.Config> buildConfigByNameMap() {
    return configStore
      .entrySet()
      .stream()
      .map(entry -> Maps.immutableEntry(entry.getKey(), entry.getValue().getConfig()))
      .filter(entry -> entry.getValue().getRowsCount() > 0)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Optional<Prefab.ConfigValue> safeResolve(String key) {
    try {
      return configResolver.getConfigValue(key);
    } catch (ConfigValueException configValueException) {
      LOG.warn("error evaluating config {} ", key, configValueException);
      return Optional.empty();
    }
  }

  public long getHighwaterMark() {
    return configLoader.getHighwaterMark();
  }

  public synchronized void loadConfigsFromLocalFile() {
    loadConfigs(configLoader.loadFromJsonFile(), ConfigClient.Source.LOCAL_FILE);
  }

  public synchronized void loadConfigs(
    Prefab.Configs configs,
    ConfigClient.Source source
  ) {
    final long startingHighWaterMark = configLoader.getHighwaterMark();
    Provenance provenance = new Provenance(source);
    configLoader.setConfigs(configs, provenance);
    if (configLoader.getHighwaterMark() > startingHighWaterMark) {
      LOG.info(
        "Found new checkpoint with highwater id {} from {} in project {} environment: {} with {} configs",
        configLoader.getHighwaterMark(),
        provenance,
        configs.getConfigServicePointer().getProjectId(),
        configs.getConfigServicePointer().getProjectEnvId(),
        configs.getConfigsCount()
      );
    } else {
      LOG.debug(
        "Checkpoint with highwater with highwater id {} from {}. No changes.",
        configLoader.getHighwaterMark(),
        provenance.getSource()
      );
    }
  }

  /**
   * set the localMap
   */
  private void makeLocal() {
    configStore.set(configLoader.calcConfig());
  }

  public String contentsString() {
    return configResolver.contentsString();
  }

  public Collection<String> getKeys() {
    return configResolver.getKeys();
  }

  public boolean containsKey(String key) {
    return configResolver.containsKey(key);
  }

  public Optional<Prefab.ConfigValue> getConfigValue(
    String key,
    LookupContext lookupContext
  ) {
    return configResolver.getConfigValue(key, lookupContext);
  }

  public Optional<Prefab.ConfigValue> getConfigValue(String key) {
    return configResolver.getConfigValue(key);
  }

  public ConfigResolver getResolver() {
    return configResolver;
  }

  public Optional<Match> getMatch(String key, LookupContext lookupContext) {
    return configResolver.getMatch(key, lookupContext);
  }

  public Optional<Match> getRawMatch(String key, LookupContext lookupContext) {
    return configResolver.getRawMatch(key, lookupContext);
  }
}
