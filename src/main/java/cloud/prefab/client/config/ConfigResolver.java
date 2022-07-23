package cloud.prefab.client.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;


public class ConfigResolver {
  private static final Logger LOG = LoggerFactory.getLogger(ConfigResolver.class);

  private final String NAMESPACE_DELIMITER = "\\.";

  private final PrefabCloudClient baseClient;
  private final ConfigLoader configLoader;
  private AtomicReference<Map<String, ResolverElement>> localMap = new AtomicReference<>(new HashMap<>());


  private long projectEnvId = 0;

  public ConfigResolver(PrefabCloudClient baseClient, ConfigLoader configLoader) {
    this.baseClient = baseClient;
    this.configLoader = configLoader;
    makeLocal();
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

  public void update() {
    makeLocal();
  }

  public ConfigResolver setProjectEnvId(long projectEnvId) {
    this.projectEnvId = projectEnvId;
    return this;
  }

  /**
   * pre-evaluate all config values for our env_key and namespace so that lookups are simple
   */
  private void makeLocal() {
    Map<String, ResolverElement> store = new HashMap<>();
    configLoader.calcConfig().forEach((key, config) -> {

      List<ResolverElement> l = config.getRowsList().stream().map((row) -> {
//        LOG.info("eval {}", row);
//        LOG.info("row projectID {}", row.getProjectEnvId());
        if (row.getProjectEnvId() != 0) { //protobuf is set
          if (row.getProjectEnvId() == projectEnvId) {
            if (!row.getNamespace().isEmpty()) {
              NamespaceMatch match = evaluateMatch(row.getNamespace(), baseClient.getNamespace());
              if (match.isMatch()) {
                return new ResolverElement(2 + match.getPartCount(), config, row.getValue(), row.getNamespace());
              } else {
                return null;
              }
            } else {
              return new ResolverElement(1, config, row.getValue(), String.format("%d", projectEnvId));
            }
          } else {
            return null;
          }
        }
        return new ResolverElement(0, config, row.getValue(), "default");

      }).filter(Objects::nonNull).sorted().collect(Collectors.toList());

      if (!l.isEmpty()) {
        final ResolverElement resolverElement = l.get(l.size() - 1);
        store.put(key, resolverElement);
      }
    });


    localMap.set(store);
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
    return new NamespaceMatch(matches.stream().allMatch(b -> b),
        matches.stream().filter(b -> b).count());
  }

  public Collection<String> getKeys() {
    return localMap.get().keySet();
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
      return MoreObjects.toStringHelper(this)
          .add("match", match)
          .add("partCount", partCount)
          .toString();
    }
  }


}
