package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;

public class TestUtils {

  public static Prefab.ConfigValue getStringConfigValue(String s) {
    return Prefab.ConfigValue.newBuilder().setString(s).build();
  }

  public static Prefab.ConfigValue getIntConfigValue(long value) {
    return Prefab.ConfigValue.newBuilder().setInt(value).build();
  }
}
