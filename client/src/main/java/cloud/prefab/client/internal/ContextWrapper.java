package cloud.prefab.client.internal;

import cloud.prefab.domain.Prefab;
import java.util.Collections;
import java.util.Map;

public class ContextWrapper {

  public static final ContextWrapper EMPTY = new ContextWrapper(Collections.emptyMap());

  private final Map<String, Prefab.ConfigValue> configValueMap;

  public ContextWrapper(Map<String, Prefab.ConfigValue> configValueMap) {
    this.configValueMap = configValueMap;
  }

  public Map<String, Prefab.ConfigValue> getConfigValueMap() {
    return configValueMap;
  }

  public static ContextWrapper empty() {
    return EMPTY;
  }
}
