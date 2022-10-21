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
  private final AtomicReference<ImmutableMap<String, ResolverElement>> localMap = new AtomicReference<>(
    ImmutableMap.of()
  );

  private long projectEnvId = 0;

  public ConfigResolver(PrefabCloudClient baseClient, ConfigLoader configLoader) {
    this.baseClient = baseClient;
    this.configLoader = configLoader;
  }

  public Optional<Prefab.ConfigValue> getConfigValue(String key) {
    final ResolverElement resolverElement = localMap.get().get(key);
    if (resolverElement != null) {
      return Optional.of(resolverElement.getConfigValue());
    }
    return Optional.empty();
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

  public ConfigResolver setProjectEnvId(long projectEnvId) {
    this.projectEnvId = projectEnvId;
    return this;
  }

  /**
   * pre-evaluate all config values for our env_key and namespace so that lookups are simple
   */
  private void makeLocal() {
    ImmutableMap.Builder<String, ResolverElement> store = ImmutableMap.builder();

    configLoader
      .calcConfig()
      .forEach((key, config) -> {
        List<ResolverElement> l = config
          .getRowsList()
          .stream()
          .map(row -> {
            //        LOG.info("eval {}", row);
            //        LOG.info("row projectID {}", row.getProjectEnvId());
            if (row.getProjectEnvId() != 0) { //protobuf is set
              if (row.getProjectEnvId() == projectEnvId) {
                if (!row.getNamespace().isEmpty()) {
                  NamespaceMatch match = evaluateMatch(
                    row.getNamespace(),
                    baseClient.getOptions().getNamespace()
                  );
                  if (match.isMatch()) {
                    return new ResolverElement(
                      2 + match.getPartCount(),
                      config,
                      row.getValue(),
                      row.getNamespace()
                    );
                  } else {
                    return null;
                  }
                } else {
                  return new ResolverElement(
                    1,
                    config,
                    row.getValue(),
                    String.format("%d", projectEnvId)
                  );
                }
              } else {
                return null;
              }
            }
            return new ResolverElement(0, config, row.getValue(), "default");
          })
          .filter(Objects::nonNull)
          .sorted()
          .collect(Collectors.toList());

        if (!l.isEmpty()) {
          final ResolverElement resolverElement = l.get(l.size() - 1);
          store.put(key, resolverElement);
        }
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

    for (Map.Entry<String, ResolverElement> entry : localMap.get().entrySet()) {
      sb.append(padded(entry.getKey(), 30));
      sb.append(padded(toS(entry.getValue().getConfigValue()), 90));
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
