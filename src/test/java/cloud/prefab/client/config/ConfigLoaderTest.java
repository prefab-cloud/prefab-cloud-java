package cloud.prefab.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.domain.Prefab;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigLoaderTest {

  private ConfigLoader configLoader;
  private Map<String, ConfigElement> stringConfigDeltaMap;

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
    assertThat(getValue("sample_int").getInt()).isEqualTo(123);
    assertThat(getValue("sample_double").getDouble()).isEqualTo(12.12);
    assertThat(getValue("sample_bool").getBool()).isEqualTo(true);

    assertValueOfConfigIs("value from override in default", "sample_to_override");
  }

  @Test
  public void testLoggersCapitalization() {
    assertValueOfConfigIsLogLevel(Prefab.LogLevel.INFO, "log-level.tests.capitalized");
    assertValueOfConfigIsLogLevel(Prefab.LogLevel.INFO, "log-level.tests.uncapitalized");
    assertValueOfConfigIsLogLevel(Prefab.LogLevel.DEBUG, "log-level.tests");
  }

  private void assertValueOfConfigIs(String expectedValue, String configKey) {
    assertThat(getValue(configKey).getString()).isEqualTo(expectedValue);
  }

  private void assertValueOfConfigIsLogLevel(
    Prefab.LogLevel expectedValue,
    String configKey
  ) {
    assertThat(getValue(configKey).getLogLevel()).isEqualTo(expectedValue);
  }

  private Prefab.ConfigValue getValue(String configKey) {
    return configLoader
      .calcConfig()
      .get(configKey)
      .getConfig()
      .getRowsList()
      .get(0)
      .getValues(0)
      .getValue();
  }

  @Test
  public void test_nested() {
    assertValueOfConfigIs("nested value", "nested.values.string");
    assertValueOfConfigIs("top level", "nested.values");
    assertValueOfConfigIsLogLevel(Prefab.LogLevel.DEBUG, "log-level.tests");
    assertValueOfConfigIsLogLevel(Prefab.LogLevel.WARN, "log-level");
    assertValueOfConfigIsLogLevel(Prefab.LogLevel.WARN, "log-level.tests.nested");
    assertValueOfConfigIsLogLevel(Prefab.LogLevel.ERROR, "log-level.tests.nested.deeply");
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
    assertThat(getValue("sample_int").getInt()).isEqualTo(1);

    configLoader.set(cd(4, "sample_int", 4));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(4);
    assertThat(getValue("sample_int").getInt()).isEqualTo(4);

    configLoader.set(cd(2, "sample_int", 2));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(4);
    assertThat(getValue("sample_int").getInt()).isEqualTo(4);
  }

  @Test
  public void testAPIPrecedence() {
    configLoader.calcConfig();

    assertThat(getValue("sample_int").getInt()).isEqualTo(123);
    configLoader.set(cd(2, "sample_int", 456));
    assertThat(getValue("sample_int").getInt()).isEqualTo(456);
  }

  @Test
  public void testLoadingTombstonesRemoves() {
    assertThat(configLoader.calcConfig().get("val_from_api")).isNull();

    configLoader.set(cd(2, "val_from_api", 456));
    assertThat(getValue("val_from_api").getInt()).isEqualTo(456);

    configLoader.set(
      new ConfigElement(
        Prefab.Config.newBuilder().setId(2).setKey("val_from_api").build(),
        new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit_tests")
      )
    );
    assertThat(configLoader.calcConfig().get("val_from_api")).isNull();
  }

  private ConfigElement cd(int id, String key, int val) {
    return new ConfigElement(
      Prefab.Config
        .newBuilder()
        .setId(id)
        .setKey(key)
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .addValues(
              Prefab.ConditionalValue
                .newBuilder()
                .setValue(Prefab.ConfigValue.newBuilder().setInt(val).build())
                .build()
            )
        )
        .build(),
      new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit_tests")
    );
  }
}
