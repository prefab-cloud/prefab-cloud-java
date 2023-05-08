package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A set of utility methods to create ConfigValues from java objects
 */
public class ConfigValueUtils {

  public static final Logger LOG = LoggerFactory.getLogger(ConfigValueUtils.class);

  public static Prefab.ConfigValue fromString(String string) {
    return Prefab.ConfigValue.newBuilder().setString(string).build();
  }

  public static Map<String, Prefab.ConfigValue> fromStringMap(
    Map<String, String> stringStringMap
  ) {
    ImmutableMap.Builder<String, Prefab.ConfigValue> builder = ImmutableMap.<String, Prefab.ConfigValue>builder();
    for (Map.Entry<String, String> stringStringEntry : stringStringMap.entrySet()) {
      builder.put(stringStringEntry.getKey(), fromString(stringStringEntry.getValue()));
    }
    return builder.build();
  }

  public static Optional<String> coerceToString(Prefab.ConfigValue configValue) {
    switch (configValue.getTypeCase()) {
      case STRING:
        return Optional.of(configValue.getString());
      case DOUBLE:
        return Optional.of(String.valueOf(configValue.getDouble()));
      case INT:
        return Optional.of(String.valueOf(configValue.getInt()));
      case BOOL:
        return Optional.of(String.valueOf(configValue.getBool()));
      case LOG_LEVEL:
        return Optional.of(configValue.getLogLevel().name());
      case STRING_LIST:
        return Optional.of(
          configValue
            .getStringList()
            .getValuesList()
            .stream()
            .collect(Collectors.joining(","))
        );
      default:
        LOG.debug(
          "Encountered unexpected type {} of configValue to coerce to string",
          configValue.getTypeCase()
        );
        return Optional.empty();
    }
  }
}
