package cloud.prefab.client;

import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.client.value.Value;
import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

public interface ConfigClient {
  ConfigResolver getResolver();

  /**
   * Evaluates a configuration based on context set in the environment
   * ie set via {@link ContextStore#addContext(PrefabContext) addContext}
   * @param key name of the config to evaluate
   * @return a value that will be evaluated at runtime based on the context from the current scope
   */
  Value<String> liveString(String key);

  /**
   * Evaluates a configuration based on context set in the environment
   * ie set via {@link ContextStore#addContext(PrefabContext) addContext}
   * @param key name of the config to evaluate
   * @return a value that will be evaluated at runtime based on the context from the current scope
   */
  Value<Boolean> liveBoolean(String key);

  /**
   * Evaluates a configuration based on context set in the environment
   * ie set via {@link ContextStore#addContext(PrefabContext) addContext}
   * @param key name of the config to evaluate
   * @return a value that will be evaluated at runtime based on the context from the current scope
   */
  Value<Long> liveLong(String key);

  /**
   * Evaluates a configuration based on context set in the environment
   * ie set via {@link ContextStore#addContext(PrefabContext) addContext}
   * @param key name of the config to evaluate
   * @return a value that will be evaluated at runtime based on the context from the current scope
   */
  Value<Double> liveDouble(String key);

  /**
   * Evaluates a configuration based on context set in the environment
   * ie set via {@link ContextStore#addContext(PrefabContext) addContext}
   * @param key name of the config to evaluate
   * @return
   */
  Optional<Prefab.ConfigValue> get(String key);

  /**
   * Evaluates a configuration based on the arguments
   * @param configKey name of the config
   * @param properties additional context to use to evaluate the config. Will be added to existing context as documented in {@link ContextStore#addContext(PrefabContext) addcontext}
   * @return the evaluated context
   * @see ConfigClient#get(String, PrefabContextSetReadable)
   */
  @Deprecated
  Optional<Prefab.ConfigValue> get(
    String configKey,
    Map<String, Prefab.ConfigValue> properties
  );

  /**
   * Evaluates a configuration based on the arguments
   * @param configKey name of the config eg `cloud.prefab.client.ConfigClient`
   * @param prefabContext additional context to use to evaluate the config. Will be added to existing context as documented in {@link ContextStore#addContext(PrefabContext) addcontext} Pass Null or {@link PrefabContextSetReadable#EMPTY} to keep context as is
   * @return the evaluated context
   * @see ConfigClient#get(String, PrefabContextSetReadable)
   */
  Optional<Prefab.ConfigValue> get(
    String configKey,
    @Nullable PrefabContextSetReadable prefabContext
  );

  boolean addConfigChangeListener(ConfigChangeListener configChangeListener);

  boolean removeConfigChangeListener(ConfigChangeListener configChangeListener);

  void reportLoggerUsage(String loggerName, Prefab.LogLevel logLevel, long count);

  /**
   * Evaluates a configuration based on the arguments
   * @param loggerName name of the logger eg
   * @param prefabContext additional context to use to evaluate the config. Will be added to existing context as documented in {@link ContextStore#addContext(PrefabContext) addcontext}
   * @return the evaluated context
   * @see ConfigClient#get(String, PrefabContextSetReadable)
   */
  Optional<Prefab.LogLevel> getLogLevel(
    String loggerName,
    @Nullable PrefabContextSetReadable prefabContext
  );

  Optional<Prefab.LogLevel> getLogLevel(String loggerName);

  boolean isReady();

  public ContextStore getContextStore();

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
