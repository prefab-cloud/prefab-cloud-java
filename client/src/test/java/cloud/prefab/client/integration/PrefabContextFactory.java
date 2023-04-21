package cloud.prefab.client.integration;

import cloud.prefab.client.config.ConfigLoader;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrefabContextFactory {

  static final Logger LOG = LoggerFactory.getLogger(PrefabContextFactory.class);

  public static PrefabContextSetReadable from(Map<String, Map<String, Object>> context) {
    if (context == null) {
      return PrefabContextSetReadable.EMPTY;
    }
    PrefabContextSet prefabContextSet = new PrefabContextSet();
    for (Map.Entry<String, Map<String, Object>> stringMapEntry : context.entrySet()) {
      prefabContextSet.addContext(
        PrefabContext.fromMap(
          stringMapEntry.getKey(),
          fromLevel2Map(stringMapEntry.getKey(), stringMapEntry.getValue())
        )
      );
    }
    return prefabContextSet;
  }

  private static Map<String, Prefab.ConfigValue> fromLevel2Map(
    String contextType,
    Map<String, Object> values
  ) {
    ImmutableMap.Builder<String, Prefab.ConfigValue> builder = ImmutableMap.builder();

    for (Map.Entry<String, Object> stringObjectEntry : values.entrySet()) {
      String key = stringObjectEntry.getKey();
      Object value = stringObjectEntry.getValue();

      if (value instanceof Map) {
        LOG.info(
          "Context {} has unhandled Map entry under key {} with value {}",
          contextType,
          key,
          value
        );
      }
      builder.put(key, ConfigLoader.configValueFromObj(key, value));
    }
    return builder.buildKeepingLast();
  }
}
