package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;

public class ResolverElement implements Comparable<ResolverElement> {


  private final int matchSize;
  private final Prefab.Config config;
  private final Prefab.ConfigValue configValue;
  private final String match;

  public ResolverElement(int matchSize, Prefab.Config config, Prefab.ConfigValue configValue, String match) {
    this.matchSize = matchSize;
    this.config = config;
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
    return config;
  }

  public int getMatchSize() {
    return matchSize;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("matchSize", matchSize)
        .add("config", config)
        .add("configValue", configValue)
        .add("match", match)
        .toString();
  }
}
