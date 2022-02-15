package cloud.prefab.client.config;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigResolver {

  private final PrefabCloudClient baseClient;
  private final ConfigLoader configLoader;
  private AtomicReference<Map<String, ResolverElement>> localMap = new AtomicReference<>(new HashMap<>());

  public ConfigResolver(PrefabCloudClient baseClient, ConfigLoader configLoader) {
    this.baseClient = baseClient;
    this.configLoader = configLoader;
    makeLocal();
  }


  public Optional<Prefab.ConfigValue> getConfigValue(String key) {
    final ResolverElement resolverElement = localMap.get().get(key);
    if (resolverElement != null) {
      return Optional.of(resolverElement.getConfigDelta().getDefault());
    }
    return Optional.empty();
  }

  public void update() {
    makeLocal();
  }

  private void makeLocal() {

    Map<String, ResolverElement> map = new HashMap<>();

    String baseNamespace = baseClient.getNamespace();

    configLoader.calcConfig().forEach((key, value) -> {
      String property = key;
      String propertyNamespace = "";
      final String[] split = key.split(":");
      if (split.length > 1) {
        propertyNamespace = split[0];
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < split.length; i++) {
          sb.append(split[i]);
        }
        property = sb.toString();
      }

      if (propertyNamespace.equals("") || baseNamespace.startsWith(propertyNamespace)) {
        final ResolverElement existing = map.get(property);
        if (existing == null) {
          map.put(property, new ResolverElement(value, propertyNamespace));
        } else if (existing.getNamespace().split("\\.").length < propertyNamespace.split("\\.").length) {
          map.put(property, new ResolverElement(value, propertyNamespace));
        }
      }
    });

    localMap.set(map);
  }

//  def export_api_deltas
//  @config_loader.get_api_deltas
//  end
//

}
