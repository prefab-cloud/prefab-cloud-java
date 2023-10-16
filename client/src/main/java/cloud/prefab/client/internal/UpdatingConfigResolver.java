package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
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
  private ConfigResolver configResolver;

  public UpdatingConfigResolver(
    ConfigLoader configLoader,
    WeightedValueEvaluator weightedValueEvaluator,
    ConfigStoreDeltaCalculator configStoreDeltaCalculator
  ) {
    this.configLoader = configLoader;
    this.configStoreDeltaCalculator = configStoreDeltaCalculator;
    this.configStore = new ConfigStoreImpl();
    this.configResolver = new ConfigResolver(configStore, weightedValueEvaluator);
  }

  /**
   * Return the changed config values since last update()
   */
  public List<ConfigChangeEvent> update() {
    // store the old map
    final Map<String, Optional<Prefab.ConfigValue>> before = configStore
      .entrySet()
      .stream()
      .collect(
        Collectors.toMap(
          Map.Entry::getKey,
          e -> configResolver.getConfigValue(e.getKey())
        )
      );

    // load the new map
    makeLocal();

    // build the new map
    final Map<String, Optional<Prefab.ConfigValue>> after = configStore
      .entrySet()
      .stream()
      .collect(
        Collectors.toMap(
          Map.Entry::getKey,
          e -> configResolver.getConfigValue(e.getKey())
        )
      );

    return configStoreDeltaCalculator.computeChangeEvents(before, after);
  }

  public long getHighwaterMark() {
    return configLoader.getHighwaterMark();
  }

  public synchronized void loadConfigs(
    Prefab.Configs configs,
    ConfigClient.Source source
  ) {
    setProjectEnvId(configs);

    final long startingHighWaterMark = configLoader.getHighwaterMark();
    Provenance provenance = new Provenance(source);

    configLoader.setDefaultContext(configs.getDefaultContext());

    for (Prefab.Config config : configs.getConfigsList()) {
      configLoader.set(new ConfigElement(config, provenance));
    }

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

  public PrefabContextSetReadable getDefaultContext() {
    return configStore.getDefaultContext();
  }

  public String contentsString() {
    return configResolver.contentsString();
  }

  public boolean setProjectEnvId(Prefab.Configs configs) {
    return configResolver.setProjectEnvId(configs);
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
}
