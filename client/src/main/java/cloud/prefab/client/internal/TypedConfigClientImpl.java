package cloud.prefab.client.internal;

import cloud.prefab.client.TypedConfigClient;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import java.time.Duration;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TypedConfigClientImpl implements TypedConfigClient {

  private static final Logger LOG = LoggerFactory.getLogger(TypedConfigClientImpl.class);

  private final ConfigClientCore configClientCore;

  TypedConfigClientImpl(ConfigClientCore configClient) {
    this.configClientCore = configClient;
  }

  @Override
  public boolean getBoolean(
    String key,
    boolean defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    try {
      return configClientCore
        .get(key, context)
        .filter(cv -> cv.getTypeCase() == Prefab.ConfigValue.TypeCase.BOOL)
        .map(Prefab.ConfigValue::getBool)
        .orElse(defaultValue);
    } catch (Throwable t) {
      LOG.debug("Error processing config {} [boolean] returning default value", key, t);
      return defaultValue;
    }
  }

  @Override
  public long getLong(
    String key,
    long defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    try {
      return configClientCore
        .get(key, context)
        .filter(cv -> cv.getTypeCase() == Prefab.ConfigValue.TypeCase.INT)
        .map(Prefab.ConfigValue::getInt)
        .orElse(defaultValue);
    } catch (Throwable t) {
      LOG.debug("Error processing config {} [long] returning default value", key, t);
      return defaultValue;
    }
  }

  @Override
  public double getDouble(
    String key,
    double defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    try {
      return configClientCore
        .get(key, context)
        .filter(cv -> cv.getTypeCase() == Prefab.ConfigValue.TypeCase.DOUBLE)
        .map(Prefab.ConfigValue::getDouble)
        .orElse(defaultValue);
    } catch (Throwable t) {
      LOG.debug("Error processing config {} [double] returning default value", key, t);
      return defaultValue;
    }
  }

  @Override
  public String getString(
    String key,
    String defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    try {
      return configClientCore
        .get(key, context)
        .filter(cv -> cv.getTypeCase() == Prefab.ConfigValue.TypeCase.STRING)
        .map(Prefab.ConfigValue::getString)
        .orElse(defaultValue);
    } catch (Throwable t) {
      LOG.debug("Error processing config {} [String] returning default value", key, t);
      return defaultValue;
    }
  }

  @Override
  public List<String> getStringList(
    String key,
    List<String> defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    try {
      return configClientCore
        .get(key, context)
        .filter(cv -> cv.getTypeCase() == Prefab.ConfigValue.TypeCase.STRING_LIST)
        .map(Prefab.ConfigValue::getStringList)
        .map(Prefab.StringList::getValuesList)
        .map(x -> (List<String>) x)
        .orElse(defaultValue);
    } catch (Throwable t) {
      LOG.debug(
        "Error processing config {} [StringList] returning default value",
        key,
        t
      );
      return defaultValue;
    }
  }

  @Override
  public Duration getDuration(
    String key,
    Duration defaultValue,
    @Nullable PrefabContextSetReadable context
  ) {
    try {
      return configClientCore
        .get(key, context)
        .filter(cv -> cv.getTypeCase() == Prefab.ConfigValue.TypeCase.DURATION)
        .map(ConfigValueUtils::asDuration)
        .orElse(defaultValue);
    } catch (Throwable t) {
      LOG.debug("Error processing config {} [Duration] returning default value", key, t);
      return defaultValue;
    }
  }
}
