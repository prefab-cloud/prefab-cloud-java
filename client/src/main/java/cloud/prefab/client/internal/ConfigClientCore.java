package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

public interface ConfigClientCore {
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
}
