package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;

public class ResolverElement {
  private Prefab.ConfigDelta configDelta;
  private String namespace;

  public ResolverElement(Prefab.ConfigDelta configDelta, String namespace) {
    this.configDelta = configDelta;
    this.namespace = namespace;
  }

  public Prefab.ConfigDelta getConfigDelta() {
    return configDelta;
  }

  public String getNamespace() {
    return namespace;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("configDelta", configDelta)
        .add("namespace", namespace)
        .toString();
  }
}
