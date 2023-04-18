package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * A set of utility methods to create ConfigValues from java objects
 */
public class ConfigValueFactory {

  static Prefab.ConfigValue fromString(String string) {
    return Prefab.ConfigValue.newBuilder().setString(string).build();
  }

  public static Map<String, Prefab.ConfigValue> fromStringMap(
    Map<String, String> stringStringMap
  ) {
    ImmutableMap.Builder<String, Prefab.ConfigValue> builder = ImmutableMap.<String, Prefab.ConfigValue>builder();
    for (Map.Entry<String, String> stringStringEntry : stringStringMap.entrySet()) {
      builder.put(stringStringEntry.getValue(), fromString(stringStringEntry.getValue()));
    }
    return builder.build();
  }
}
