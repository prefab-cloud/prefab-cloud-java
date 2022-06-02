package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConfigLoader {
  private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

  private ConcurrentMap<String, Prefab.Config> apiConfig = new ConcurrentHashMap<>();
  private long highwaterMark = 0;

  private Map<String, Prefab.Config> classPathConfig;

  private Map<String, Prefab.Config> overrideConfig;

  public ConfigLoader() {
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
    Optional<Prefab.Config> highwaterMarkDelta = apiConfig.values().stream().max(new Comparator<Prefab.Config>() {
      @Override
      public int compare(Prefab.Config o1, Prefab.Config o2) {
        return (int) (o1.getId() - o2.getId());
      }
    });

    highwaterMark = highwaterMarkDelta.isPresent() ? highwaterMarkDelta.get().getId() : 0;
  }


  private Map<String, Prefab.Config> loadClasspathConfig() {
    Map<String, Prefab.Config> rtn = new HashMap<>();
    try {
      ResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver();
      Resource[] mappingLocations = patternResolver.getResources("classpath*:.prefab*config.yaml");

      for (Resource mappingLocation : mappingLocations) {
        loadFileTo(mappingLocation.getFile(), rtn);
      }
    } catch (IOException e) {
      LOG.error(e.getMessage());
      e.printStackTrace();
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
    return Prefab.Config.newBuilder().addRows(Prefab.ConfigRow.newBuilder().setValue(builder.build()).build()).build();
  }

  private Map<String, Prefab.Config> loadOverrideConfig() {

    Map<String, Prefab.Config> rtn = new HashMap<>();

    File dir = new File(System.getProperty("user.home"));
    File[] files = dir.listFiles((dir1, name) -> name.matches("\\.prefab.*config\\.yaml"));

    for (File file : files) {
      loadFileTo(file, rtn);
    }

    return rtn;
  }

  private void loadFileTo(File file, Map<String, Prefab.Config> rtn) {
    try {
      Yaml yaml = new Yaml();
      Map<String, Object> obj = null;
      obj = yaml.load(new FileInputStream(file));
      obj.forEach((k, v) -> {
        rtn.put(k, toValue(v));
      });
    } catch (FileNotFoundException e) {
      LOG.error(e.getMessage());

      e.printStackTrace();
    }
  }

  public long getHighwaterMark() {
    return highwaterMark;
  }

}
