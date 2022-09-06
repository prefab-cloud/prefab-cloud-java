package cloud.prefab.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigLoaderTest {

  private ConfigLoader configLoader;
  private Map<String, Prefab.Config> stringConfigDeltaMap;

  @BeforeEach
  public void setup() {
    configLoader =
      new ConfigLoader(
        new Options()
          .setConfigOverrideDir("src/test/resources/override_directory")
          .setPrefabEnvs(List.of("unit_tests"))
      );

    stringConfigDeltaMap = configLoader.calcConfig();
  }

  @Test
  public void testLoad() {
    assertValueOfConfigIs("test sample value", "sample");
    assertThat(
      stringConfigDeltaMap.get("sample_int").getRowsList().get(0).getValue().getInt()
    )
      .isEqualTo(123);
    assertThat(
      stringConfigDeltaMap
        .get("sample_double")
        .getRowsList()
        .get(0)
        .getValue()
        .getDouble()
    )
      .isEqualTo(12.12);
    assertThat(
      stringConfigDeltaMap.get("sample_bool").getRowsList().get(0).getValue().getBool()
    )
      .isEqualTo(true);

    assertValueOfConfigIs("value from override in default", "sample_to_override");
  }

  private void assertValueOfConfigIs(String expectedValue, String configKey) {
    assertThat(
      stringConfigDeltaMap.get(configKey).getRowsList().get(0).getValue().getString()
    )
      .isEqualTo(expectedValue);
  }

  @Test
  public void test_nested() {
    assertValueOfConfigIs("nested value", "nested.values.string");
    assertValueOfConfigIs("top level", "nested.values");
    assertValueOfConfigIs("error", "logging.app");
    assertValueOfConfigIs("warn", "logging.app.controller.hello");
    assertValueOfConfigIs("info", "logging.app.controller.hello.index");
  }

  @Test
  public void testHighwater() {
    assertThat(configLoader.getHighwaterMark()).isEqualTo(0);

    configLoader.set(cd(1, "sample_int", 456));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(1);

    configLoader.set(cd(5, "sample_int", 456));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(5);

    configLoader.set(cd(3, "sample_int", 456));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(5);
  }

  @Test
  public void testKeepsMostRecent() {
    assertThat(configLoader.getHighwaterMark()).isEqualTo(0);

    configLoader.set(cd(1, "sample_int", 1));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(1);
    assertThat(
      configLoader.calcConfig().get("sample_int").getRowsList().get(0).getValue().getInt()
    )
      .isEqualTo(1);

    configLoader.set(cd(4, "sample_int", 4));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(4);
    assertThat(
      configLoader.calcConfig().get("sample_int").getRowsList().get(0).getValue().getInt()
    )
      .isEqualTo(4);

    configLoader.set(cd(2, "sample_int", 2));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(4);
    assertThat(
      configLoader.calcConfig().get("sample_int").getRowsList().get(0).getValue().getInt()
    )
      .isEqualTo(4);
  }

  @Test
  public void testAPIPrecedence() {
    configLoader.calcConfig();

    assertThat(
      configLoader.calcConfig().get("sample_int").getRowsList().get(0).getValue().getInt()
    )
      .isEqualTo(123);
    configLoader.set(cd(2, "sample_int", 456));
    assertThat(
      configLoader.calcConfig().get("sample_int").getRowsList().get(0).getValue().getInt()
    )
      .isEqualTo(456);
  }

  @Test
  public void testLoadingTombstonesRemoves() {
    assertThat(configLoader.calcConfig().get("val_from_api")).isNull();

    configLoader.set(cd(2, "val_from_api", 456));
    assertThat(
      configLoader
        .calcConfig()
        .get("val_from_api")
        .getRowsList()
        .get(0)
        .getValue()
        .getInt()
    )
      .isEqualTo(456);

    configLoader.set(Prefab.Config.newBuilder().setId(2).setKey("val_from_api").build());
    assertThat(configLoader.calcConfig().get("val_from_api")).isNull();
  }

  private Prefab.Config cd(int id, String key, int val) {
    return Prefab.Config
      .newBuilder()
      .setId(id)
      .setKey(key)
      .addRows(
        Prefab.ConfigRow
          .newBuilder()
          .setValue(Prefab.ConfigValue.newBuilder().setInt(val).build())
      )
      .build();
  }
}
