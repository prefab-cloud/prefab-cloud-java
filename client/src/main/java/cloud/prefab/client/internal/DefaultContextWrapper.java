package cloud.prefab.client.internal;

import cloud.prefab.domain.Prefab;
import java.util.Collections;
import java.util.Map;

public class DefaultContextWrapper {

  private final Map<String, Prefab.ConfigValue> configValueMap;

  public DefaultContextWrapper(Map<String, Prefab.ConfigValue> configValueMap) {
    this.configValueMap = configValueMap;
  }

  public Map<String, Prefab.ConfigValue> getConfigValueMap() {
    return configValueMap;
  }

  public static DefaultContextWrapper empty() {
    return new DefaultContextWrapper(Collections.emptyMap());
  }
}
