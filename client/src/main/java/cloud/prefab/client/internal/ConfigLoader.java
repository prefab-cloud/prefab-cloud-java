package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.client.config.logging.AbstractLoggingListener;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import cloud.prefab.domain.Prefab.LogLevel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.protobuf.util.JsonFormat;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
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

  private final AtomicLong projectEnvId = new AtomicLong(0);

  private final AtomicReference<PrefabContextSetReadable> configIncludedContext = new AtomicReference<>(
    PrefabContextSetReadable.EMPTY
  );
  private final PrefabContextSetReadable globalContext;

  public ConfigLoader(Options options) {
    this.options = options;
    this.apiConfig = new ConcurrentHashMap<>();
    this.highwaterMark = new AtomicLong(0);
    this.classPathConfig = loadClasspathConfig();
    this.overrideConfig = loadOverrideConfig();
    this.globalContext =
      options
        .getGlobalContext()
        .filter(Predicate.not(PrefabContextSetReadable::isEmpty))
        .orElse(PrefabContextSetReadable.EMPTY);
  }

  /**
   * start with the classpath config files
   * merge the live API configs on next
   * layer the overrides on last
   */
  public MergedConfigData calcConfig() {
    ImmutableMap.Builder<String, ConfigElement> builder = ImmutableMap.builder();
    builder.putAll(classPathConfig);
    builder.putAll(apiConfig);
    builder.putAll(overrideConfig);
    return new MergedConfigData(
      builder.buildKeepingLast(),
      projectEnvId.get(),
      globalContext,
      configIncludedContext.get()
    );
  }

  private PrefabContextSetReadable getConfigIncludedContext(Prefab.Configs configs) {
    return PrefabContextSet.from(configs.getDefaultContext());
  }

  public synchronized void setConfigs(Prefab.Configs configs, Provenance provenance) {
    for (Prefab.Config config : configs.getConfigsList()) {
      set(new ConfigElement(config, provenance), false);
    }
    recomputeHighWaterMark();
    projectEnvId.set(configs.getConfigServicePointer().getProjectEnvId());
    configIncludedContext.set(getConfigIncludedContext(configs));
  }

  @VisibleForTesting
  void set(ConfigElement configElement) {
    set(configElement, true);
  }

  private void set(ConfigElement configElement, boolean calculateHighWaterMark) {
    final Prefab.Config config = configElement.getConfig();
    final ConfigElement existing = apiConfig.get(config.getKey());

    if (existing == null || existing.getConfig().getId() <= config.getId()) {
      if (config.getRowsList().isEmpty()) {
        apiConfig.remove(config.getKey());
      } else {
        apiConfig.put(config.getKey(), configElement);
      }
    }
    if (calculateHighWaterMark) {
      recomputeHighWaterMark();
    }
  }

  private InputStream loadFileFromDiskOrResources(String filename) throws IOException {
    Path path = Paths.get(filename);
    if (Files.exists(path)) {
      return Files.newInputStream(path);
    }
    InputStream streamFromResources = getClass().getResourceAsStream(filename);
    if (streamFromResources == null) {
      throw new RuntimeException("File %s not found, cannot proceed");
    }
    return streamFromResources;
  }

  public Prefab.Configs loadFromJsonFile() {
    if (!options.isLocalDatafileMode()) {
      throw new IllegalStateException("no local data file specified");
    }
    LOG.info("Loading configs from {}", options.getLocalDatafile());
    try (
      InputStream inputStream = loadFileFromDiskOrResources(options.getLocalDatafile());
      Reader reader = new BufferedReader(new InputStreamReader(inputStream))
    ) {
      Prefab.Configs.Builder builder = Prefab.Configs.newBuilder();
      JsonFormat.parser().merge(reader, builder);
      return builder.build();
    } catch (IOException e) {
      throw new RuntimeException(e);
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
    if (!options.isLocalDatafileMode()) {
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
    if (!options.isLocalDatafileMode()) {
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
    obj.forEach((k, v) -> loadKeyValue(k, v, builder, source, sourceLocation));
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
