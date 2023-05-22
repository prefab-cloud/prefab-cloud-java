package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.client.config.logging.AbstractLoggingListener;
import cloud.prefab.domain.Prefab;
import cloud.prefab.domain.Prefab.LogLevel;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class ConfigLoader {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

  private static final ImmutableSet<Prefab.Criterion.CriterionOperator> PROPERTY_OPERATORS = Sets.immutableEnumSet(
    Prefab.Criterion.CriterionOperator.PROP_IS_ONE_OF,
    Prefab.Criterion.CriterionOperator.PROP_IS_NOT_ONE_OF,
    Prefab.Criterion.CriterionOperator.PROP_ENDS_WITH_ONE_OF,
    Prefab.Criterion.CriterionOperator.PROP_ENDS_WITH_ONE_OF
  );

  private final Options options;
  private final ConcurrentMap<String, ConfigElement> apiConfig;
  private final AtomicLong highwaterMark;
  private final ImmutableMap<String, ConfigElement> classPathConfig;
  private final ImmutableMap<String, ConfigElement> overrideConfig;

  public ConfigLoader(Options options) {
    this.options = options;
    this.apiConfig = new ConcurrentHashMap<>();
    this.highwaterMark = new AtomicLong(0);
    this.classPathConfig = loadClasspathConfig();
    this.overrideConfig = loadOverrideConfig();
  }

  /**
   * start with the classpath config files
   * merge the live API configs on next
   * layer the overrides on last
   */
  public Map<String, ConfigElement> calcConfig() {
    ImmutableMap.Builder<String, ConfigElement> builder = ImmutableMap.builder();

    builder.putAll(classPathConfig);
    builder.putAll(apiConfig);
    builder.putAll(overrideConfig);

    return builder.buildKeepingLast();
  }

  public void set(ConfigElement configElement) {
    final Prefab.Config config = configElement.getConfig();
    final ConfigElement existing = apiConfig.get(config.getKey());

    if (existing == null || existing.getConfig().getId() <= config.getId()) {
      if (config.getRowsList().isEmpty()) {
        apiConfig.remove(config.getKey());
      } else {
        apiConfig.put(config.getKey(), configElement);
      }
      recomputeHighWaterMark();
    }
  }

  private void recomputeHighWaterMark() {
    long highwaterMarkDelta = apiConfig
      .values()
      .stream()
      .map(ConfigElement::getConfig)
      .mapToLong(Prefab.Config::getId)
      .max()
      .orElse(0L);

    highwaterMark.set(highwaterMarkDelta);
  }

  private ImmutableMap<String, ConfigElement> loadClasspathConfig() {
    ImmutableMap.Builder<String, ConfigElement> builder = ImmutableMap.builder();

    for (String env : options.getAllPrefabEnvs()) {
      final String file = String.format(".prefab.%s.config.yaml", env);

      try (
        InputStream resourceAsStream = this.getClass()
          .getClassLoader()
          .getResourceAsStream(file)
      ) {
        if (resourceAsStream == null) {
          LOG.warn("No default config file found {}", file);
        } else {
          loadFileTo(resourceAsStream, builder, ConfigClient.Source.CLASSPATH, file);
        }
      } catch (IOException e) {
        throw new RuntimeException("Error loading config from file: " + file, e);
      }
    }

    return builder.buildKeepingLast();
  }

  public static Prefab.ConfigValue configValueFromObj(String key, Object obj) {
    final Prefab.ConfigValue.Builder valueBuilder = Prefab.ConfigValue.newBuilder();
    if (obj instanceof Boolean) {
      valueBuilder.setBool((Boolean) obj);
    } else if (obj instanceof Integer) {
      valueBuilder.setInt((Integer) obj);
    } else if (obj instanceof Double) {
      valueBuilder.setDouble((Double) obj);
    } else if (obj instanceof String) {
      if (AbstractLoggingListener.keyIsLogLevel(key)) {
        valueBuilder.setLogLevel(LogLevel.valueOf(((String) obj).toUpperCase()));
      } else {
        valueBuilder.setString((String) obj);
      }
    } else if (obj instanceof List) {
      Prefab.StringList.Builder stringListBuilder = Prefab.StringList.newBuilder();
      stringListBuilder.addAllValues((List<String>) obj);
      valueBuilder.setStringList(stringListBuilder.build());
    }
    return valueBuilder.build();
  }

  private ConfigElement toFeatureFlag(
    String key,
    Map<String, Object> map,
    ConfigClient.Source source,
    String sourceLocation
  ) {
    Object value = map.get("value");
    if (value == null) {
      throw new IllegalArgumentException(
        String.format("Feature flag with key '%s' must have a 'value' set", key)
      );
    }

    Prefab.ConfigValue configValue = configValueFromObj(key, value);
    Prefab.ConditionalValue.Builder conditionalValueBuilder = Prefab.ConditionalValue
      .newBuilder()
      .setValue(configValue);

    Object criteria = map.get("criteria");
    if (criteria instanceof Map) {
      conditionalValueBuilder.addCriteria(
        toCriterion(key, (Map<String, Object>) criteria)
      );
    } else if (criteria instanceof List) {
      for (Object criterion : ((List) criteria)) {
        if (criterion instanceof Map) {
          conditionalValueBuilder.addCriteria(
            toCriterion(key, (Map<String, Object>) criteria)
          );
        }
      }
    }

    return new ConfigElement(
      Prefab.Config
        .newBuilder()
        .setConfigType(Prefab.ConfigType.FEATURE_FLAG)
        .addRows(
          Prefab.ConfigRow.newBuilder().addValues(conditionalValueBuilder.build()).build()
        )
        .build(),
      new Provenance(source, sourceLocation)
    );
  }

  private Prefab.Criterion toCriterion(String key, Map<String, Object> map) {
    Prefab.Criterion.Builder builder = Prefab.Criterion.newBuilder();

    String operatorValue = (String) map.get("operator");
    if (operatorValue == null) {
      throw new IllegalArgumentException(
        String.format(
          "Feature flag with key '%s' must have a 'operator' set in each criteria",
          key
        )
      );
    }
    Prefab.Criterion.CriterionOperator operator = Prefab.Criterion.CriterionOperator.valueOf(
      operatorValue
    );
    builder.setOperator(operator);
    String property = (String) map.get("property");
    if (PROPERTY_OPERATORS.contains(operator) && property == null) {
      throw new IllegalArgumentException(
        String.format(
          "Feature flag with key '%s' must have a 'property' set in each criteria with a property operator",
          key
        )
      );
    }
    if (property != null) {
      builder.setPropertyName(property);
    }

    Object values = map.get("values");
    if (values == null) {
      throw new IllegalArgumentException(
        String.format(
          "Feature flag with key '%s' must have 'values' set in each criteria",
          key
        )
      );
    }
    if (values != null) {
      builder.setValueToMatch(configValueFromObj(key, values));
    }
    return builder.build();
  }

  private ConfigElement toValue(
    String key,
    Object obj,
    ConfigClient.Source source,
    String sourceLocation
  ) {
    final Prefab.ConfigValue value = configValueFromObj(key, obj);

    return new ConfigElement(
      Prefab.Config
        .newBuilder()
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .addValues(Prefab.ConditionalValue.newBuilder().setValue(value).build())
            .build()
        )
        .build(),
      new Provenance(source, sourceLocation)
    );
  }

  private ImmutableMap<String, ConfigElement> loadOverrideConfig() {
    ImmutableMap.Builder<String, ConfigElement> builder = ImmutableMap.builder();

    File dir = new File(options.getConfigOverrideDir());
    for (String env : options.getAllPrefabEnvs()) {
      final String fileName = String.format(".prefab.%s.config.yaml", env);
      File file = new File(dir, fileName);

      if (file.exists()) {
        try (InputStream inputStream = new FileInputStream(file)) {
          loadFileTo(
            inputStream,
            builder,
            ConfigClient.Source.LOCAL_OVERRIDE,
            file.getAbsolutePath()
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return builder.buildKeepingLast();
  }

  private void loadFileTo(
    InputStream inputStream,
    ImmutableMap.Builder<String, ConfigElement> builder,
    ConfigClient.Source source,
    String sourceLocation
  ) {
    LOG.info("Load File {}", sourceLocation);
    Yaml yaml = new Yaml();
    Map<String, Object> obj = yaml.load(inputStream);
    obj.forEach((k, v) -> {
      loadKeyValue(k, v, builder, source, sourceLocation);
    });
  }

  private void loadKeyValue(
    String k,
    Object v,
    ImmutableMap.Builder<String, ConfigElement> builder,
    ConfigClient.Source source,
    String sourceLocation
  ) {
    if (v instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) v;
      if (map.containsKey("feature_flag")) {
        builder.put(k, toFeatureFlag(k, map, source, sourceLocation));
      } else {
        for (Map.Entry<String, Object> nest : map.entrySet()) {
          String nestedKey = String.format("%s.%s", k, nest.getKey());
          if (nest.getKey().equals("_")) {
            nestedKey = k;
          }
          loadKeyValue(nestedKey, nest.getValue(), builder, source, sourceLocation);
        }
      }
    } else {
      builder.put(k, toValue(k, v, source, sourceLocation));
    }
  }

  public long getHighwaterMark() {
    return highwaterMark.get();
  }
}