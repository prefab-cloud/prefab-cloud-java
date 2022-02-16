package cloud.prefab.client.config;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;


public class ConfigResolver {
  private final String NAMESPACE_DELIMITER = "\\.";

  private final PrefabCloudClient baseClient;
  private final ConfigLoader configLoader;
  private AtomicReference<Map<String, ResolverElement>> localMap = new AtomicReference<>(new HashMap<>());

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

  public void update() {
    makeLocal();
  }

  /**
   * pre-evaluate all config values for our env_key and namespace so that lookups are simple
   */
  private void makeLocal() {
    Map<String, ResolverElement> map = new HashMap<>();

    String baseNamespace = baseClient.getNamespace();

    configLoader.calcConfig().forEach((key, value) -> {
      String property = key;

      ResolverElement bestMatch = new ResolverElement(value.getDefault(), ResolverElement.MatchType.DEFAULT, null, null, 0);

      final Optional<Prefab.EnvironmentValues> matchingEnv = value.getEnvsList().stream().filter(ev -> ev.getEnvironment().equals(baseClient.getEnvironment())).findFirst();

      //do we have and env_values that match our env?
      if (matchingEnv.isPresent()) {
        final Prefab.EnvironmentValues environmentValues = matchingEnv.get();
        //override the top level default with env default
        bestMatch = new ResolverElement(environmentValues.getDefault(), ResolverElement.MatchType.ENV_DEFAULT, environmentValues.getEnvironment(), null, 0);

        //check all namespace_values for match
        for (Prefab.NamespaceValue nv : environmentValues.getNamespaceValuesList()) {
          NamespaceMatch match = evaluateMatch(nv.getNamespace(), baseNamespace);
          if (match.isMatch()) {
            //is this match the best match?
            if (match.getPartCount() > bestMatch.getNamespacePartMatchCount()) {
              bestMatch = new ResolverElement(nv.getConfigValue(), ResolverElement.MatchType.NAMESPACE_VALUE, environmentValues.getEnvironment(), baseNamespace, match.getPartCount());
            }
          }
        }
      }

      // feature flags are a funny case
      // we only define the variants in the default in order to be DRY
      // but we want to access them in environments, clone them over
      if(bestMatch.getConfigValue().getTypeCase() == Prefab.ConfigValue.TypeCase.FEATURE_FLAG){
        final Prefab.FeatureFlag.Builder bestMatchFeatureFlag = bestMatch.getConfigValue().getFeatureFlag().toBuilder();

        //replace variants with variants from the default
        bestMatchFeatureFlag.clearVariants();
        bestMatchFeatureFlag.addAllVariants(value.getDefault().getFeatureFlag().getVariantsList());

        final Prefab.ConfigValue updatedConfigValue = bestMatch.getConfigValue().toBuilder()
            .setFeatureFlag(bestMatchFeatureFlag.build())
            .build();
        bestMatch.setConfigValue(updatedConfigValue);
      }

      map.put(key, bestMatch);
    });

    localMap.set(map);
  }

  NamespaceMatch evaluateMatch(String namespace, String baseNamespace) {
    final String[] nsSplit = namespace.split(NAMESPACE_DELIMITER);
    final String[] baseSplit = baseNamespace.split(NAMESPACE_DELIMITER);

    int matchSize = 0;
    for (int i = 0; i < baseSplit.length; i++) {
      if (nsSplit.length <= i || nsSplit[i].equals("")) {
        return new NamespaceMatch(true, matchSize);
      }
      if (baseSplit[i].equals(nsSplit[i])) {
        matchSize += 1;
      } else {
        return new NamespaceMatch(false, matchSize);
      }
    }
    return new NamespaceMatch(true, matchSize);
  }

  public static class NamespaceMatch {
    private boolean match;
    private int partCount;

    public NamespaceMatch(boolean match, int partCount) {
      this.match = match;
      this.partCount = partCount;
    }

    public boolean isMatch() {
      return match;
    }

    public int getPartCount() {
      return partCount;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
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
