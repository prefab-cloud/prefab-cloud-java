package cloud.prefab.client;

import cloud.prefab.context.PrefabContextSetReadable;
import java.time.Duration;
import java.util.List;
import javax.annotation.Nullable;

public interface TypedConfigClient {
  /**
   * Gets the specified boolean configured value
   * @param key the name of the config
   * @param defaultValue value to return if configured value is not available
   * @param context context for config targeting
   * @return the configured value or defaultValue if the configured value does not exist or any other exception occurs
   */
  boolean getBoolean(
    String key,
    boolean defaultValue,
    @Nullable PrefabContextSetReadable context
  );

  /**
   * Gets the specified long configured value
   * @param key the name of the config
   * @param defaultValue value to return if configured value is not available
   * @param context context for config targeting
   * @return the configured value or defaultValue if the configured value does not exist or any other exception occurs
   */
  long getLong(String key, long defaultValue, @Nullable PrefabContextSetReadable context);

  /**
   * Gets the specified double configured value
   * @param key the name of the config
   * @param defaultValue value to return if configured value is not available
   * @param context context for config targeting
   * @return the configured value or defaultValue if the configured value does not exist or any other exception occurs
   */
  double getDouble(
    String key,
    double defaultValue,
    @Nullable PrefabContextSetReadable context
  );

  /**
   * Gets the specified String configured value
   * @param key the name of the config
   * @param defaultValue value to return if configured value is not available
   * @param context context for config targeting
   * @return the configured value or defaultValue if the configured value does not exist or any other exception occurs
   */
  String getString(
    String key,
    String defaultValue,
    @Nullable PrefabContextSetReadable context
  );

  /**
   * Gets the specified `List<String>` configured value
   * @param key the name of the config
   * @param defaultValue value to return if configured value is not available
   * @param context context for config targeting
   * @return the configured value or defaultValue if the configured value does not exist or any other exception occurs
   */
  List<String> getStringList(
    String key,
    List<String> defaultValue,
    @Nullable PrefabContextSetReadable context
  );

  /**
   * Gets the specified Duration configured value
   * @param key the name of the config
   * @param defaultValue value to return if configured value is not available
   * @param context context for config targeting
   * @return the configured value or defaultValue if the configured value does not exist or any other exception occurs
   */
  Duration getDuration(
    String key,
    Duration defaultValue,
    @Nullable PrefabContextSetReadable context
  );
}
