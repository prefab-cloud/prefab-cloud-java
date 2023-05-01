package cloud.prefab.client;

import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.client.value.Value;
import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

public interface ConfigClient {
  /**
   * Evaluates a configuration based on context from the ContextStore
   * ie set via {@link ContextStore#addContext(PrefabContext) addContext}
   * @param key name of the config to evaluate
   * @return a value that will be evaluated at runtime based on the context from the current scope
   */
  Value<String> liveString(String key);

  /**
   * Evaluates a configuration based on context from the ContextStore
   * ie set via {@link ContextStore#addContext(PrefabContext) addContext}
   * @param key name of the config to evaluate
   * @return a value that will be evaluated at runtime based on the context from the current scope
   */
  Value<Boolean> liveBoolean(String key);

  /**
   * Evaluates a configuration based on context from the ContextStore
   * ie set via {@link ContextStore#addContext(PrefabContext) addContext}
   * @param key name of the config to evaluate
   * @return a value that will be evaluated at runtime based on the context from the current scope
   */
  Value<Long> liveLong(String key);

  /**
   * Evaluates a configuration based on context from the ContextStore
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
   * @return the current value of the config
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
   * @return the current value of the config
   */
  Optional<Prefab.ConfigValue> get(
    String configKey,
    @Nullable PrefabContextSetReadable prefabContext
  );

  /**
   * Evaluates all configurations of ConfigType = CONFIG
   * @param prefabContext additional context to use to evaluate the config. Will be added to existing context as documented in {@link ContextStore#addContext(PrefabContext) addcontext} Pass Null or {@link PrefabContextSetReadable#EMPTY} to keep context as is
   * @return a Map with the config's key as the key, the current ConfigValue as map's value
   */
  Map<String, Prefab.ConfigValue> getAll(
    @Nullable PrefabContextSetReadable prefabContext
  );

  /**
   * @return all known config keys where the ConfigType = CONFIG
   */
  Collection<String> getAllKeys();

  /**
   * Adds a listener to config change events. A listener will only hear events that occur after this method is called.
   * To register at client startup add listener using {@link Options#addConfigChangeListener(ConfigChangeListener)} instead
   * @param configChangeListener
   * @return
   */
  boolean addConfigChangeListener(ConfigChangeListener configChangeListener);

  boolean removeConfigChangeListener(ConfigChangeListener configChangeListener);

  /**
   * This method is for primarily internal use by Prefab's logging filter implementations
   * @param loggerName the fully qualified name of a logger to report
   * @param logLevel the log level
   * @param count the number of log messages
   */
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

  /**
   * Check if the client has completed initialization. Will not block
   * @return true if client is ready
   */
  boolean isReady();

  /**
   * Get the configured {@link ContextStore}
   * Can be set using {@link Options#setContextStore(ContextStore)}
   * @return the ContextStore implementation
   */
  ContextStore getContextStore();

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
