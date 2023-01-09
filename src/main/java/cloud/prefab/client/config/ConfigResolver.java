package cloud.prefab.client.config;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigResolver.class);

  private static final String NAMESPACE_DELIMITER = "\\.";

  private final PrefabCloudClient baseClient;
  private final ConfigLoader configLoader;
  private final AtomicReference<ImmutableMap<String, ConfigElement>> localMap = new AtomicReference<>(
    ImmutableMap.of()
  );
  private final WeightedValueEvaluator weightedValueEvaluator;

  private long projectEnvId = 0;

  public ConfigResolver(PrefabCloudClient baseClient, ConfigLoader configLoader) {
    this.baseClient = baseClient;
    this.configLoader = configLoader;
    this.weightedValueEvaluator = new WeightedValueEvaluator();
  }

  public Optional<Prefab.ConfigValue> getConfigValue(String key) {
    return getConfigValue(key, new HashMap<>());
  }

  public Optional<Prefab.ConfigValue> getConfigValue(
    String key,
    Map<String, Prefab.ConfigValue> properties
  ) {
    if (!localMap.get().containsKey(key)) {
      return Optional.empty();
    }
    final ConfigElement configElement = localMap.get().get(key);

    final Optional<Match> match = findMatch(configElement, properties);

    if (match.isPresent()) {
      return Optional.of(match.get().getConfigValue());
    } else {
      return Optional.empty();
    }
  }

  public Optional<Prefab.Config> getConfig(String key) {
    final ResolverElement resolverElement = localMap.get().get(key);
    if (resolverElement != null) {
      return Optional.of(resolverElement.getConfig());
    }
    return Optional.empty();
  }

  /**
   * Return the changed config values since last update()
   */
  public synchronized List<ConfigChangeEvent> update() {
    // store the old map
    final Map<String, Prefab.ConfigValue> before = localMap
      .get()
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getConfigValue()));

    // load the new map
    makeLocal();

    // build the new map
    final Map<String, Prefab.ConfigValue> after = localMap
      .get()
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getConfigValue()));

    MapDifference<String, Prefab.ConfigValue> delta = Maps.difference(before, after);
    if (delta.areEqual()) {
      return ImmutableList.of();
    } else {
      ImmutableList.Builder<ConfigChangeEvent> changeEvents = ImmutableList.builder();

      // removed config values
      delta
        .entriesOnlyOnLeft()
        .forEach((key, value) ->
          changeEvents.add(
            new ConfigChangeEvent(key, Optional.of(value), Optional.empty())
          )
        );

      // added config values
      delta
        .entriesOnlyOnRight()
        .forEach((key, value) ->
          changeEvents.add(
            new ConfigChangeEvent(key, Optional.empty(), Optional.of(value))
          )
        );

      // changed config values
      delta
        .entriesDiffering()
        .forEach((key, values) ->
          changeEvents.add(
            new ConfigChangeEvent(
              key,
              Optional.of(values.leftValue()),
              Optional.of(values.rightValue())
            )
          )
        );

      return changeEvents.build();
    }
  }

  public boolean setProjectEnvId(Prefab.Configs configs) {
    if (configs.hasConfigServicePointer()) {
      this.projectEnvId = configs.getConfigServicePointer().getProjectEnvId();
      return true;
    }
    return false;
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

    localMap.set(store.buildKeepingLast());
  }

  NamespaceMatch evaluateMatch(String namespace, String baseNamespace) {
    final String[] nsSplit = namespace.split(NAMESPACE_DELIMITER);
    final String[] baseSplit = baseNamespace.split(NAMESPACE_DELIMITER);

    List<Boolean> matches = new ArrayList<>();
    for (int i = 0; i < nsSplit.length; i++) {
      if (baseSplit.length <= i) {
        matches.add(false);
        continue;
      }
      matches.add(nsSplit[i].equals(baseSplit[i]));
    }
    return new NamespaceMatch(
      matches.stream().allMatch(b -> b),
      matches.stream().filter(b -> b).count()
    );
  }

  public Collection<String> getKeys() {
    return localMap.get().keySet();
  }

  public String contentsString() {
    StringBuilder sb = new StringBuilder("\n");
    List<String> sortedKeys = new ArrayList(localMap.get().keySet());
    Collections.sort(sortedKeys);
    for (String key : sortedKeys) {
      ResolverElement resolverElement = localMap.get().get(key);
      sb.append(padded(key, 30));
      sb.append(padded(toS(resolverElement.getConfigValue()), 40));
      sb.append(padded(resolverElement.provenance(), 90));
      sb.append("\n");
    }
    System.out.println(sb.toString());
    return sb.toString();
  }

  private String toS(Prefab.ConfigValue configValue) {
    if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.STRING) {
      return configValue.getString();
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.INT) {
      return Long.toString(configValue.getInt());
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.BOOL) {
      return Boolean.toString(configValue.getBool());
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.BYTES) {
      return "Bytes";
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.DOUBLE) {
      return Double.toString(configValue.getDouble());
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.SEGMENT) {
      return "Segment";
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.FEATURE_FLAG) {
      return "FeatureFlag";
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.LOG_LEVEL) {
      return configValue.getLogLevel().toString();
    } else {
      return "Unknown";
    }
  }

  private String padded(String s, int size) {
    return String.format(
      "%-" + size + "s",
      s.substring(0, Math.min(s.length(), size - 1))
    );
  }

  public static class NamespaceMatch {

    private boolean match;
    private int partCount;

    public NamespaceMatch(boolean match, long partCount) {
      this.match = match;
      this.partCount = (int) partCount;
    }

    public boolean isMatch() {
      return match;
    }

    public int getPartCount() {
      return partCount;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      NamespaceMatch that = (NamespaceMatch) o;
      return match == that.match && partCount == that.partCount;
    }

    @Override
    public int hashCode() {
      return Objects.hash(match, partCount);
    }

    @Override
    public String toString() {
      return MoreObjects
        .toStringHelper(this)
        .add("match", match)
        .add("partCount", partCount)
        .toString();
    }
  }
}
