package cloud.prefab.client.config;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class UpdatingConfigResolver {

  private final ConfigLoader configLoader;

  private final PrefabCloudClient baseClient;
  private final ConfigStoreImpl configStore;
  private ConfigResolver configResolver;

  public UpdatingConfigResolver(
    PrefabCloudClient baseClient,
    ConfigLoader configLoader,
    WeightedValueEvaluator weightedValueEvaluator
  ) {
    this.baseClient = baseClient;
    this.configLoader = configLoader;
    this.configStore = new ConfigStoreImpl();
    this.configResolver = new ConfigResolver(configStore, weightedValueEvaluator);
  }

  /**
   * Return the changed config values since last update()
   */
  public synchronized List<ConfigChangeEvent> update() {
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

    MapDifference<String, Optional<Prefab.ConfigValue>> delta = Maps.difference(
      before,
      after
    );
    if (delta.areEqual()) {
      return ImmutableList.of();
    } else {
      ImmutableList.Builder<ConfigChangeEvent> changeEvents = ImmutableList.builder();

      // removed config values
      delta
        .entriesOnlyOnLeft()
        .forEach((key, value) ->
          changeEvents.add(new ConfigChangeEvent(key, value, Optional.empty()))
        );

      // added config values
      delta
        .entriesOnlyOnRight()
        .forEach((key, value) ->
          changeEvents.add(new ConfigChangeEvent(key, Optional.empty(), value))
        );

      // changed config values
      delta
        .entriesDiffering()
        .forEach((key, values) ->
          changeEvents.add(
            new ConfigChangeEvent(key, values.leftValue(), values.rightValue())
          )
        );

      return changeEvents.build();
    }
  }

  /**
   * set the localMap
   */
  private void makeLocal() {
    ImmutableMap.Builder<String, ConfigElement> store = ImmutableMap.builder();

    configLoader
      .calcConfig()
      .forEach((key, configElement) -> {
        store.put(key, configElement);
      });

    configStore.set(store.buildKeepingLast());
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
}
