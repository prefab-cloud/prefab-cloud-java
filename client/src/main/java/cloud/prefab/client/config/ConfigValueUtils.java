package cloud.prefab.client.config;

import cloud.prefab.client.util.FlexibleDurationParser;
import cloud.prefab.domain.Prefab;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A set of utility methods to convert ConfigValues to and from java objects
 */
public class ConfigValueUtils {

  public static final Logger LOG = LoggerFactory.getLogger(ConfigValueUtils.class);

  public static Prefab.ConfigValue from(String string) {
    return Prefab.ConfigValue.newBuilder().setString(string).build();
  }

  public static Prefab.ConfigValue from(List<String> stringList) {
    return Prefab.ConfigValue
      .newBuilder()
      .setStringList(Prefab.StringList.newBuilder().addAllValues(stringList))
      .build();
  }

  public static Prefab.ConfigValue from(boolean bool) {
    return Prefab.ConfigValue.newBuilder().setBool(bool).build();
  }

  public static Prefab.ConfigValue from(long number) {
    return Prefab.ConfigValue.newBuilder().setInt(number).build();
  }

  public static Prefab.ConfigValue from(int number) {
    return Prefab.ConfigValue.newBuilder().setInt(number).build();
  }

  public static Prefab.ConfigValue from(double number) {
    return Prefab.ConfigValue.newBuilder().setDouble(number).build();
  }

  public static Prefab.ConfigValue from(Prefab.LogLevel logLevel) {
    return Prefab.ConfigValue.newBuilder().setLogLevel(logLevel).build();
  }

  public static Prefab.ConfigValue from(Duration duration) {
    return Prefab.ConfigValue
      .newBuilder()
      .setDuration(
        Prefab.IsoDuration.newBuilder().setDefinition(duration.toString()).build()
      )
      .build();
  }

  public static Map<String, Prefab.ConfigValue> fromStringMap(
    Map<String, String> stringStringMap
  ) {
    ImmutableMap.Builder<String, Prefab.ConfigValue> builder = ImmutableMap.<String, Prefab.ConfigValue>builder();
    for (Map.Entry<String, String> stringStringEntry : stringStringMap.entrySet()) {
      builder.put(stringStringEntry.getKey(), from(stringStringEntry.getValue()));
    }
    return builder.build();
  }

  public static Optional<String> toDisplayString(Prefab.ConfigValue configValue) {
    if (configValue.hasConfidential() && configValue.getConfidential()) {
      return Optional.of("**** [confidential]");
    }
    if (configValue.hasDecryptWith()) {
      return Optional.of("**** [encrypted]");
    }
    if (configValue.hasProvided()) {
      // TODO
      // resolve the provided value? indicate where it was provided from?
    }

    return coerceToString(configValue);
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
        return Optional.of(String.join(",", configValue.getStringList().getValuesList()));
      case BYTES:
        return Optional.of(
          BaseEncoding.base16().encode(configValue.getBytes().toByteArray())
        );
      case DURATION:
        return Optional.of(configValue.getDuration().getDefinition());
      default:
        LOG.debug(
          "Encountered unexpected type {} of configValue to coerce to string",
          configValue.getTypeCase()
        );
        return Optional.empty();
    }
  }

  public static Optional<Object> asObject(Prefab.ConfigValue configValue) {
    switch (configValue.getTypeCase()) {
      case STRING:
        return Optional.of(configValue.getString());
      case DOUBLE:
        return Optional.of(configValue.getDouble());
      case INT:
        return Optional.of(configValue.getInt());
      case BOOL:
        return Optional.of(configValue.getBool());
      case LOG_LEVEL:
        return Optional.of(configValue.getLogLevel());
      case STRING_LIST:
        return Optional.of(configValue.getStringList().getValuesList());
      case DURATION:
        return Optional.of(asDuration(configValue));
      case JSON:
        return Optional.of(configValue.getJson().getJson());
      default:
        LOG.debug(
          "Encountered unexpected type {} of configValue to coerce to string",
          configValue.getTypeCase()
        );
        return Optional.empty();
    }
  }

  public static Duration asDuration(Prefab.ConfigValue configValue) {
    Preconditions.checkArgument(
      configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.DURATION,
      "Config value is not of type DURATION"
    );
    return FlexibleDurationParser.parse(configValue.getDuration().getDefinition());
  }

  @Deprecated
  /**
   * @deprecated see {@link #from(String)}
   */
  public static Prefab.ConfigValue fromString(String string) {
    return from(string);
  }

  public static boolean isNumber(Prefab.ConfigValue configValue) {
    return configValue.hasDouble() || configValue.hasInt();
  }

  public static Optional<Instant> asDate(Prefab.ConfigValue configValue) {
    if (configValue.hasInt()) {
      return Optional.of(Instant.ofEpochMilli(configValue.getInt()));
    }
    if (configValue.hasString()) {
      try {
        return Optional.of(ZonedDateTime.parse(configValue.getString()).toInstant());
      } catch (Exception e) {
        // ignore
      }
    }
    return Optional.empty();
  }
}
