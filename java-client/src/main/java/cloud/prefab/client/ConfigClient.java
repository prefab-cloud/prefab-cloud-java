package cloud.prefab.client;

import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.client.value.Value;
import cloud.prefab.domain.Prefab;
import java.util.Map;
import java.util.Optional;

public interface ConfigClient {
  ConfigResolver getResolver();

  Value<String> liveString(String key);

  Value<Boolean> liveBoolean(String key);

  Value<Long> liveLong(String key);

  Value<Double> liveDouble(String key);

  Optional<Prefab.ConfigValue> get(String key);

  Optional<Prefab.ConfigValue> get(
    String key,
    Map<String, Prefab.ConfigValue> properties
  );

  void upsert(String key, Prefab.ConfigValue configValue);

  void upsert(Prefab.Config config);

  boolean addConfigChangeListener(ConfigChangeListener configChangeListener);

  boolean removeConfigChangeListener(ConfigChangeListener configChangeListener);

  public enum Source {
    REMOTE_API_GRPC,
    STREAMING,
    REMOTE_CDN,
    LOCAL_ONLY,
    INIT_TIMEOUT,
    CLASSPATH,
    OVERRIDE,
  }
}
