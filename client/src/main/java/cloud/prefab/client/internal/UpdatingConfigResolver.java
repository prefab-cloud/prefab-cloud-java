package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.Provenance;
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
  private final ConfigStoreDeltaCalculator configStoreDeltaCalculator;

  private final ConfigStoreImpl configStore;
  private final ConfigResolver configResolver;

  public UpdatingConfigResolver(
    ConfigLoader configLoader,
    WeightedValueEvaluator weightedValueEvaluator,
    ConfigStoreDeltaCalculator configStoreDeltaCalculator
  ) {
    this.configLoader = configLoader;
    this.configStoreDeltaCalculator = configStoreDeltaCalculator;
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
  public List<ConfigChangeEvent> update() {
    // catch exceptions resolving, treat as absent
    // store the old map
    Map<String, Optional<Prefab.ConfigValue>> before = buildValueMap();
    // load the new map
    makeLocal();

    // build the new map
    Map<String, Optional<Prefab.ConfigValue>> after = buildValueMap();
    return configStoreDeltaCalculator.computeChangeEvents(before, after);
  }

  private Map<String, Optional<Prefab.ConfigValue>> buildValueMap() {
    return configStore
      .entrySet()
      .stream()
      .map(entry ->
        safeResolve(entry.getKey()).map(cv -> Maps.immutableEntry(entry.getKey(), cv))
      )
      .flatMap(Optional::stream)
      .collect(
        Collectors.toMap(
          Map.Entry::getKey,
          e -> configResolver.getConfigValue(e.getKey())
        )
      );
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
