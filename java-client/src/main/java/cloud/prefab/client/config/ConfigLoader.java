package cloud.prefab.client.config;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.config.logging.AbstractLoggingListener;
import cloud.prefab.domain.Prefab;
import cloud.prefab.domain.Prefab.LogLevel;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class ConfigLoader {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

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
    }
    return valueBuilder.build();
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
          loadFileTo(inputStream, builder, ConfigClient.Source.OVERRIDE, fileName);
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
      builder.put(k, toValue(k, v, source, sourceLocation));
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

      for (Map.Entry<String, Object> nest : map.entrySet()) {
        String nestedKey = String.format("%s.%s", k, nest.getKey());
        if (nest.getKey().equals("_")) {
          nestedKey = k;
        }
        loadKeyValue(nestedKey, nest.getValue(), builder, source, sourceLocation);
      }
    } else {
      builder.put(k, toValue(k, v, source, sourceLocation));
    }
  }

  public long getHighwaterMark() {
    return highwaterMark.get();
  }
}
