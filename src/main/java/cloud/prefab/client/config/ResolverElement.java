package cloud.prefab.client.config;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;
import java.util.Objects;

public class ResolverElement implements Comparable<ResolverElement> {

  private final int matchSize;
  private final ConfigElement configElement;
  private final Prefab.ConfigValue configValue;
  private final String match;

  public ResolverElement(
    int matchSize,
    ConfigElement configElement,
    Prefab.ConfigValue configValue,
    String match
  ) {
    this.matchSize = matchSize;
    this.configElement = configElement;
    this.configValue = configValue;
    this.match = match;
  }

  @Override
  public int compareTo(ResolverElement o) {
    return matchSize - o.getMatchSize();
  }

  public Prefab.ConfigValue getConfigValue() {
    return configValue;
  }

  public Prefab.Config getConfig() {
    return configElement.getConfig();
  }

  public int getMatchSize() {
    return matchSize;
  }

  public String provenance() {
    return MoreObjects
      .toStringHelper(this)
      .add("source", configElement.getSource())
      .add("sourceLocation", configElement.getSourceLocation())
      .add("match", match)
      .toString();
  }

  @Override
  public String toString() {
    return MoreObjects
      .toStringHelper(this)
      .add("matchSize", matchSize)
      .add("config", configElement)
      .add("configValue", configValue)
      .add("match", match)
      .toString();
  }
}
