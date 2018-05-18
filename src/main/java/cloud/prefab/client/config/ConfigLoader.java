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

  private ConcurrentMap<String, Prefab.ConfigDelta> apiConfig = new ConcurrentHashMap<>();
  private long highwaterMark = 0;

  private Map<String, Prefab.ConfigDelta> classPathConfig;

  private Map<String, Prefab.ConfigDelta> overrideConfig;

  public ConfigLoader() {
    classPathConfig = loadClasspathConfig();
    overrideConfig = loadOverrideConfig();
  }

  /**
   * start with the classpath config files
   * merge the live API configs on next
   * layer the overrides on last
   */
  public Map<String, Prefab.ConfigDelta> calcConfig() {
    Map<String, Prefab.ConfigDelta> rtn = new HashMap<>(classPathConfig);
    apiConfig.forEach((k, v) -> {
      rtn.put(k, v);
    });

    overrideConfig.forEach((k, v) -> {
      rtn.put(k, v);
    });
    return rtn;
  }

  public void set(Prefab.ConfigDelta delta) {
    final Prefab.ConfigDelta existing = apiConfig.get(delta.getKey());

    if (existing == null || existing.getId() <= delta.getId()) {


      if (delta.getValue().getTypeCase() == Prefab.ConfigValue.TypeCase.TYPE_NOT_SET) {
        apiConfig.remove(delta.getKey());
      } else {
        apiConfig.put(delta.getKey(), delta);
      }
      recomputeHighWaterMark();
    }
  }

  private void recomputeHighWaterMark() {
    Optional<Prefab.ConfigDelta> highwaterMarkDelta = apiConfig.values().stream().max(new Comparator<Prefab.ConfigDelta>() {
      @Override
      public int compare(Prefab.ConfigDelta o1, Prefab.ConfigDelta o2) {
        return (int) (o1.getId() - o2.getId());
      }
    });

    highwaterMark = highwaterMarkDelta.isPresent() ? highwaterMarkDelta.get().getId() : 0;
  }


  private Map<String, Prefab.ConfigDelta> loadClasspathConfig() {
    Map<String, Prefab.ConfigDelta> rtn = new HashMap<>();
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

  private Prefab.ConfigDelta toValue(Object obj) {

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
    return Prefab.ConfigDelta.newBuilder().setValue(builder.build()).build();
  }

  private Map<String, Prefab.ConfigDelta> loadOverrideConfig() {

    Map<String, Prefab.ConfigDelta> rtn = new HashMap<>();

    File dir = new File(System.getProperty("user.home"));
    File[] files = dir.listFiles((dir1, name) -> name.matches("\\.prefab.*config\\.yaml"));

    for (File file : files) {
      loadFileTo(file, rtn);
    }

    return rtn;
  }

  private void loadFileTo(File file, Map<String, Prefab.ConfigDelta> rtn) {
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

//
//  def rm(key)
//  @api_config.delete key
//      end
//
//  def get_api_deltas
//  deltas = Prefab::ConfigDeltas.new
//  @api_config.each_value do |config_value|
//  deltas.deltas << config_value
//      end
//  deltas
//      end
//
//  private

}
