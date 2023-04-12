package cloud.prefab.client;

import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.client.value.Value;
import cloud.prefab.context.Context;
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

  Optional<Prefab.ConfigValue> get(String configKey, Context context);

  Optional<Prefab.ConfigValue> get(String configKey, Optional<Context> context);

  boolean addConfigChangeListener(ConfigChangeListener configChangeListener);

  boolean removeConfigChangeListener(ConfigChangeListener configChangeListener);

  void reportLoggerUsage(String loggerName, Prefab.LogLevel logLevel, long count);

  Optional<Prefab.LogLevel> getLogLevel(
    String loggerName,
    Map<String, Prefab.ConfigValue> properties
  );

  Optional<Prefab.LogLevel> getLogLevel(String loggerName, Context context);
  Optional<Prefab.LogLevel> getLogLevel(String loggerName, Optional<Context> context);

  Optional<Prefab.LogLevel> getLogLevelFromStringMap(
    String loggerName,
    Map<String, String> properties
  );

  boolean isReady();

  enum Source {
    REMOTE_API,
    REMOTE_API_GRPC,
    STREAMING_SSE,
    STREAMING,
    REMOTE_CDN,
    LOCAL_ONLY,
    INIT_TIMEOUT,
    CLASSPATH,
    LOCAL_OVERRIDE,
  }
}
