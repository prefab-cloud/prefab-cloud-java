package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;

public class ResolverElement {

  public enum MatchType {
    DEFAULT,
    ENV_DEFAULT,
    NAMESPACE_VALUE
  }
  private Prefab.ConfigValue configValue;
  private MatchType matchType;
  private String environment;
  private String namespace;
  private int namespacePartMatchCount;

  public ResolverElement(Prefab.ConfigValue configValue, MatchType matchType, String environment, String namespace, int namespacePartMatchCount) {
    this.configValue = configValue;
    this.matchType = matchType;
    this.environment = environment;
    this.namespace = namespace;
    this.namespacePartMatchCount = namespacePartMatchCount;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("configValue", configValue)
        .add("matchType", matchType)
        .add("environment", environment)
        .add("namespace", namespace)
        .add("namespacePartMatchCount", namespacePartMatchCount)
        .toString();
  }

  public Prefab.ConfigValue getConfigValue() {
    return configValue;
  }

  public MatchType getMatchType() {
    return matchType;
  }

  public String getEnvironment() {
    return environment;
  }

  public String getNamespace() {
    return namespace;
  }

  public int getNamespacePartMatchCount() {
    return namespacePartMatchCount;
  }
}
