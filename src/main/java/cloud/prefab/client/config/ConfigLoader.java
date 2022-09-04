package cloud.prefab.client.config;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class ConfigLoader {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);
  private final PrefabCloudClient.Options options;

  private ConcurrentMap<String, Prefab.Config> apiConfig = new ConcurrentHashMap<>();
  private long highwaterMark = 0;

  private Map<String, Prefab.Config> classPathConfig;

  private Map<String, Prefab.Config> overrideConfig;

  public ConfigLoader(PrefabCloudClient.Options options) {
    this.options = options;
    classPathConfig = loadClasspathConfig();
    overrideConfig = loadOverrideConfig();
  }

  /**
   * start with the classpath config files
   * merge the live API configs on next
   * layer the overrides on last
   */
  public Map<String, Prefab.Config> calcConfig() {
    Map<String, Prefab.Config> rtn = new HashMap<>(classPathConfig);
    apiConfig.forEach((k, v) -> {
      rtn.put(k, v);
    });

    overrideConfig.forEach((k, v) -> {
      rtn.put(k, v);
    });
    return rtn;
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
    Optional<Prefab.Config> highwaterMarkDelta = apiConfig
      .values()
      .stream()
      .max(
        new Comparator<Prefab.Config>() {
          @Override
          public int compare(Prefab.Config o1, Prefab.Config o2) {
            return (int) (o1.getId() - o2.getId());
          }
        }
      );

    highwaterMark = highwaterMarkDelta.isPresent() ? highwaterMarkDelta.get().getId() : 0;
  }

  private Map<String, Prefab.Config> loadClasspathConfig() {
    Map<String, Prefab.Config> rtn = new HashMap<>();

    for (String env : options.getAllPrefabEnvs()) {
      final String file = String.format(".prefab.%s.config.yaml", env);
      final InputStream resourceAsStream =
        this.getClass().getClassLoader().getResourceAsStream(file);
      loadFileTo(resourceAsStream, rtn, file);
    }

    return rtn;
  }

  private Prefab.Config toValue(Object obj) {
    final Prefab.ConfigValue.Builder builder = Prefab.ConfigValue.newBuilder();
    if (obj instanceof Boolean) {
      builder.setBool((Boolean) obj);
    } else if (obj instanceof Integer) {
      builder.setInt((Integer) obj);
    } else if (obj instanceof Double) {
      builder.setDouble((Double) obj);
    } else if (obj instanceof String) {
      builder.setString((String) obj);
    }
    return Prefab.Config
      .newBuilder()
      .addRows(Prefab.ConfigRow.newBuilder().setValue(builder.build()).build())
      .build();
  }

  private Map<String, Prefab.Config> loadOverrideConfig() {
    Map<String, Prefab.Config> rtn = new HashMap<>();

    File dir = new File(options.getConfigOverrideDir());
    for (String env : options.getAllPrefabEnvs()) {
      final String fileName = String.format(".prefab.%s.config.yaml", env);
      File file = new File(dir, fileName);

      if (file.exists()) {
        try (InputStream inputStream = new FileInputStream(file)) {
          loadFileTo(inputStream, rtn, fileName);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    return rtn;
  }

  private void loadFileTo(
    InputStream inputStream,
    Map<String, Prefab.Config> rtn,
    String file
  ) {
    LOG.info("Load Default File {}", file);
    Yaml yaml = new Yaml();
    Map<String, Object> obj = yaml.load(inputStream);
    obj.forEach((k, v) -> {
      loadKeyValue(k, v, rtn, file);
      rtn.put(k, toValue(v));
    });
  }

  private void loadKeyValue(
    String k,
    Object v,
    Map<String, Prefab.Config> rtn,
    String file
  ) {
    if (v instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) v;

      for (Map.Entry<String, Object> nest : map.entrySet()) {
        String nestedKey = String.format("%s.%s", k, nest.getKey());
        if (nest.getKey().equals("_")) {
          nestedKey = k;
        }
        loadKeyValue(nestedKey, nest.getValue(), rtn, file);
      }
    } else {
      rtn.put(k, toValue(v));
    }
  }

  public long getHighwaterMark() {
    return highwaterMark;
  }
}
