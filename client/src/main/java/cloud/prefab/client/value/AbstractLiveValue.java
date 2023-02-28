package cloud.prefab.client.value;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLiveValue<T> implements Value<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractLiveValue.class);

  private final ConfigClient configClient;
  protected final String key;

  public AbstractLiveValue(ConfigClient configClient, String key) {
    this.configClient = configClient;
    this.key = key;
  }

  /**
   * May throw UndefinedKeyException or TypeMismatchException
   *
   * @return
   */
  @Override
  public T get() {
    final Optional<T> fromConfig = getMaybe();
    return fromConfig.orElseThrow(() ->
      new UndefinedKeyException(
        "No config value for key " + key + " and no default defined."
      )
    );
  }

  /**
   * Will not throw exceptions on key mismatch or undefined key.
   * May throw TypeMismatchException if entered data is not of appropriate type
   */

  @Override
  public Optional<T> getMaybe() {
    return getFromConfig();
  }

  /**
   * Will not throw exceptions on key mismatch or undefined key.
   *
   * @param defaultValue
   * @return
   */
  @Override
  public T orElse(T defaultValue) {
    try {
      return getMaybe().orElse(defaultValue);
    } catch (TypeMismatchException e) {
      LOG.warn("Type mismatch for key {}. Returning default value", key);
      return defaultValue;
    }
  }

  /**
   * Will not throw exceptions on key mismatch or undefined key.
   */
  @Override
  public T orElseGet(Supplier<T> defaultValueSupplier) {
    try {
      return getMaybe().orElseGet(defaultValueSupplier);
    } catch (TypeMismatchException e) {
      LOG.warn("Type mismatch for key {}. returning default value", key);
      return defaultValueSupplier.get();
    }
  }

  /**
   * Will not throw exceptions on key mismatch or undefined key.
   */
  @Override
  public T orNull() {
    try {
      return getMaybe().orElse(null);
    } catch (TypeMismatchException e) {
      LOG.warn("Type mismatch for key {}.", key);
      return null;
    }
  }

  public abstract Optional<T> resolve(Prefab.ConfigValue value);

  private Optional<T> getFromConfig() {
    final Optional<Prefab.ConfigValue> configValue = configClient.get(key);
    if (configValue.isPresent()) {
      final Optional<T> resolve = resolve(configValue.get());
      if (resolve.isPresent()) {
        return resolve;
      } else {
        throw new TypeMismatchException(
          "Config value for key " +
          key +
          " is not of the expected type. Is " +
          configValue.get().getTypeCase()
        );
      }
    } else {
      return Optional.empty();
    }
  }
}
