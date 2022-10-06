package cloud.prefab.client.config;

import cloud.prefab.client.Options;
import cloud.prefab.client.config.logging.AbstractLoggingListener;
import cloud.prefab.domain.Prefab;
import cloud.prefab.domain.Prefab.Config;
import cloud.prefab.domain.Prefab.LogLevel;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
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
  private final ConcurrentMap<String, Prefab.Config> apiConfig;
  private final AtomicLong highwaterMark;
  private final ImmutableMap<String, Config> classPathConfig;
  private final ImmutableMap<String, Prefab.Config> overrideConfig;

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
  public Map<String, Prefab.Config> calcConfig() {
    ImmutableMap.Builder<String, Prefab.Config> builder = ImmutableMap.builder();

    builder.putAll(classPathConfig);
    builder.putAll(apiConfig);
    builder.putAll(overrideConfig);

    return builder.buildKeepingLast();
  }

  public void set(Prefab.Config config) {
    final Prefab.Config existing = apiConfig.get(config.getKey());

    if (existing == null || existing.getId() <= config.getId()) {
      if (config.getRowsList().isEmpty()) {
        apiConfig.remove(config.getKey());
      } else {
        apiConfig.put(config.getKey(), config);
      }
      recomputeHighWaterMark();
    }
  }

  private void recomputeHighWaterMark() {
    long highwaterMarkDelta = apiConfig
      .values()
      .stream()
      .mapToLong(Prefab.Config::getId)
      .max()
      .orElse(0L);

    highwaterMark.set(highwaterMarkDelta);
  }

  private ImmutableMap<String, Prefab.Config> loadClasspathConfig() {
    ImmutableMap.Builder<String, Prefab.Config> builder = ImmutableMap.builder();

    Path dir = Paths.get(options.getConfigOverrideDir());
    for (String env : options.getAllPrefabEnvs()) {
      final String file = String.format(".prefab.%s.config.yaml", env);

      try (
        InputStream resourceAsStream = this.getClass()
          .getClassLoader()
          .getResourceAsStream(dir.resolve(file).toString())
      ) {
        if (resourceAsStream == null) {
          LOG.warn("No default config file found {}", file);
        } else {
          loadFileTo(resourceAsStream, builder, file);
        }
      } catch (IOException e) {
        throw new RuntimeException("Error loading config from file: " + file, e);
      }
    }

    return builder.buildKeepingLast();
  }

  private Prefab.Config toValue(String key, Object obj) {
    final Prefab.ConfigValue.Builder builder = Prefab.ConfigValue.newBuilder();
    if (obj instanceof Boolean) {
      builder.setBool((Boolean) obj);
    } else if (obj instanceof Integer) {
      builder.setInt((Integer) obj);
    } else if (obj instanceof Double) {
      builder.setDouble((Double) obj);
    } else if (obj instanceof String) {
      if (AbstractLoggingListener.keyIsLogLevel(key)) {
        builder.setLogLevel(LogLevel.valueOf((String) obj));
      } else {
        builder.setString((String) obj);
      }
    }
    return Prefab.Config
      .newBuilder()
      .addRows(Prefab.ConfigRow.newBuilder().setValue(builder.build()).build())
      .build();
  }

  private ImmutableMap<String, Prefab.Config> loadOverrideConfig() {
    ImmutableMap.Builder<String, Prefab.Config> builder = ImmutableMap.builder();

    File dir = new File(options.getConfigOverrideDir());
    for (String env : options.getAllPrefabEnvs()) {
      final String fileName = String.format(".prefab.%s.config.yaml", env);
      File file = new File(dir, fileName);

      if (file.exists()) {
        try (InputStream inputStream = new FileInputStream(file)) {
          loadFileTo(inputStream, builder, fileName);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    return builder.buildKeepingLast();
  }

  private void loadFileTo(
    InputStream inputStream,
    ImmutableMap.Builder<String, Prefab.Config> builder,
    String file
  ) {
    LOG.info("Load File {}", file);
    Yaml yaml = new Yaml();
    Map<String, Object> obj = yaml.load(inputStream);
    obj.forEach((k, v) -> {
      loadKeyValue(k, v, builder);
      builder.put(k, toValue(k, v));
    });
  }

  private void loadKeyValue(
    String k,
    Object v,
    ImmutableMap.Builder<String, Prefab.Config> builder
  ) {
    if (v instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) v;

      for (Map.Entry<String, Object> nest : map.entrySet()) {
        String nestedKey = String.format("%s.%s", k, nest.getKey());
        if (nest.getKey().equals("_")) {
          nestedKey = k;
        }
        loadKeyValue(nestedKey, nest.getValue(), builder);
      }
    } else {
      builder.put(k, toValue(k, v));
    }
  }

  public long getHighwaterMark() {
    return highwaterMark.get();
  }
}
