package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.domain.Prefab;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ConfigLoaderTest {

  private ConfigLoader configLoader;

  void buildLoaderWithOptions(Options options) {
    configLoader = new ConfigLoader(options);
  }

  @Nested
  class JsonFileNoOverrideTests {

    @BeforeEach
    void beforeEach() {
      buildLoaderWithOptions(
        new Options()
          .setConfigOverrideDir("src/test/resources/override_directory")
          .setPrefabEnvs(List.of("default"))
          .setLocalDatafile("src/test/resources/prefab.Development.5.config.json")
      );
    }

    @Test
    void itIgnoresOverrides() {
      configLoader.setConfigs(
        configLoader.loadFromJsonFile(),
        new Provenance(ConfigClient.Source.LOCAL_FILE)
      );
      Optional<Prefab.Config> configFromJsonFileMaybe = getConfig("cool.bool.enabled");
      assertThat(configFromJsonFileMaybe).isPresent();
      assertThat(configFromJsonFileMaybe.get().hasChangedBy()).isTrue(); // the override value in .prefab.default.config.yaml wouldn't have a changedby
      assertThat(getConfig("sample"))
        .as(
          "sample from the defaults file resources/.prefab.default.config.yaml shouldn't be present"
        )
        .isEmpty();
    }
  }

  @Nested
  class UnitTestEnvTests {

    @BeforeEach
    void beforeEach() {
      buildLoaderWithOptions(
        new Options()
          .setConfigOverrideDir("src/test/resources/override_directory")
          .setPrefabEnvs(List.of("unit_tests"))
      );
    }

    @Test
    public void testLoad() {
      assertValueOfConfigIs("test sample value", "sample");
      assertThat(getValue("sample_int")).map(Prefab.ConfigValue::getInt).contains(123L);
      assertThat(getValue("sample_double"))
        .map(Prefab.ConfigValue::getDouble)
        .contains(12.12);
      assertThat(getValue("sample_bool")).map(Prefab.ConfigValue::getBool).contains(true);
      assertValueOfConfigIs("value from override in default", "sample_to_override");
    }

    @Test
    public void testLoggersCapitalization() {
      assertValueOfConfigIsLogLevel(Prefab.LogLevel.INFO, "log-level.tests.capitalized");
      assertValueOfConfigIsLogLevel(
        Prefab.LogLevel.INFO,
        "log-level.tests.uncapitalized"
      );
      assertValueOfConfigIsLogLevel(Prefab.LogLevel.DEBUG, "log-level.tests");
    }

    @Test
    void testSimpleFeatureFlagLoad() {
      assertThat(getConfig("flag_with_a_value")).isPresent();

      Prefab.Config ffWithValue = getConfig("flag_with_a_value").get();
      assertThat(ffWithValue.getConfigType()).isEqualTo(Prefab.ConfigType.FEATURE_FLAG);
      assertThat(ffWithValue.getRowsList().get(0).getValues(0).getValue().getString())
        .isEqualTo("all-features");
    }

    @Test
    void testFeatureFlagLoadWithCriteria() {
      assertThat(getConfig("in_lookup_key")).isPresent();

      Prefab.Config ffWithValue = getConfig("in_lookup_key").get();
      assertThat(ffWithValue.getConfigType()).isEqualTo(Prefab.ConfigType.FEATURE_FLAG);
      Prefab.ConditionalValue conditionalValue = ffWithValue
        .getRowsList()
        .get(0)
        .getValues(0);
      assertThat(conditionalValue.getValue().getBool()).isTrue();
      assertThat(conditionalValue.getCriteriaCount()).isEqualTo(1);
      Prefab.Criterion criteria = conditionalValue.getCriteria(0);
      assertThat(criteria)
        .isEqualTo(
          Prefab.Criterion
            .newBuilder()
            .setOperator(Prefab.Criterion.CriterionOperator.LOOKUP_KEY_IN)
            .setValueToMatch(
              Prefab.ConfigValue
                .newBuilder()
                .setStringList(
                  Prefab.StringList
                    .newBuilder()
                    .addValues("abc123")
                    .addValues("xyz987")
                    .build()
                )
                .build()
            )
            .build()
        );
    }

    @Test
    void testFeatureFlagLoadWithCriteriaAndProperty() {
      assertThat(getConfig("just_my_domain")).isPresent();

      Prefab.Config ffWithValue = getConfig("just_my_domain").get();
      assertThat(ffWithValue.getConfigType()).isEqualTo(Prefab.ConfigType.FEATURE_FLAG);
      Prefab.ConditionalValue conditionalValue = ffWithValue
        .getRowsList()
        .get(0)
        .getValues(0);
      assertThat(conditionalValue.getValue().getString()).isEqualTo("new-version");
      assertThat(conditionalValue.getCriteriaCount()).isEqualTo(1);
      Prefab.Criterion criteria = conditionalValue.getCriteria(0);
      assertThat(criteria)
        .isEqualTo(
          Prefab.Criterion
            .newBuilder()
            .setOperator(Prefab.Criterion.CriterionOperator.PROP_IS_ONE_OF)
            .setPropertyName("domain")
            .setValueToMatch(
              Prefab.ConfigValue
                .newBuilder()
                .setStringList(
                  Prefab.StringList
                    .newBuilder()
                    .addValues("prefab.cloud")
                    .addValues("example.com")
                    .build()
                )
                .build()
            )
            .build()
        );
    }

    @Test
    public void testNested() {
      assertValueOfConfigIsEmpty("nested");
      assertValueOfConfigIs("nested value", "nested.values.string");
      assertValueOfConfigIs("top level", "nested.values");
      assertValueOfConfigIs("", "log-level");
      assertValueOfConfigIsLogLevel(Prefab.LogLevel.WARN, "log-level");
      assertValueOfConfigIsLogLevel(Prefab.LogLevel.WARN, "log-level.tests.nested");
      assertValueOfConfigIsLogLevel(
        Prefab.LogLevel.ERROR,
        "log-level.tests.nested.deeply"
      );
    }

    @Test
    void nestedVsDottedFormatsAreIdentical() {
      assertValueOfConfigIsEmpty("example");
      assertValueOfConfigIsEmpty("example2");
      assertValueOfConfigIsEmpty("example.nested");
      assertValueOfConfigIsEmpty("example2.nested");
      assertValueOfConfigIs("hello", "example.nested.path");
      assertValueOfConfigIs("hello2", "example2.nested.path");
    }

    @Test
    public void testUnderscoreNestingForLogger() {
      assertValueOfConfigIsLogLevel(Prefab.LogLevel.WARN, "log-level");
    }

    @Test
    public void testUnderscoreNestingForConfig() {
      assertValueOfConfigIs("the value", "nested2");
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
      assertThat(getValue("sample_int")).map(Prefab.ConfigValue::getInt).contains(1L);

      configLoader.set(cd(4, "sample_int", 4));
      assertThat(configLoader.getHighwaterMark()).isEqualTo(4);
      assertThat(getValue("sample_int")).map(Prefab.ConfigValue::getInt).contains(4L);

      // next value is not kept because its highwater mark is 2
      configLoader.set(cd(2, "sample_int", 2));
      assertThat(configLoader.getHighwaterMark()).isEqualTo(4);
      assertThat(getValue("sample_int")).map(Prefab.ConfigValue::getInt).contains(4L);
    }

    @Test
    public void testAPIPrecedence() {
      configLoader.calcConfig();

      assertThat(getValue("sample_int"))
        .map(Prefab.ConfigValue::getInt)
        .isPresent()
        .get()
        .isEqualTo(123L);
      configLoader.set(cd(2, "sample_int", 456));
      assertThat(getValue("sample_int"))
        .map(Prefab.ConfigValue::getInt)
        .isPresent()
        .get()
        .isEqualTo(456L);
    }

    @Test
    public void testLoadingTombstonesRemoves() {
      assertThat(configLoader.calcConfig().getConfigs().get("val_from_api")).isNull();

      configLoader.set(cd(2, "val_from_api", 456));
      assertThat(getValue("val_from_api"))
        .map(Prefab.ConfigValue::getInt)
        .isPresent()
        .get()
        .isEqualTo(456L);

      configLoader.set(
        new ConfigElement(
          Prefab.Config.newBuilder().setId(2).setKey("val_from_api").build(),
          new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit_tests")
        )
      );
      assertThat(configLoader.calcConfig().getConfigs().get("val_from_api")).isNull();
    }
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

  private void assertValueOfConfigIs(String expectedValue, String configKey) {
    assertThat(getValue(configKey))
      .map(Prefab.ConfigValue::getString)
      .get()
      .isEqualTo(expectedValue);
  }

  private void assertValueOfConfigIsEmpty(String configKey) {
    assertThat(getValue(configKey)).isEmpty();
  }

  private void assertValueOfConfigIsLogLevel(
    Prefab.LogLevel expectedValue,
    String configKey
  ) {
    assertThat(getValue(configKey))
      .map(Prefab.ConfigValue::getLogLevel)
      .contains(expectedValue);
  }

  private Optional<Prefab.Config> getConfig(String configKey) {
    return Optional
      .ofNullable(configLoader.calcConfig().getConfigs().get(configKey))
      .map(ConfigElement::getConfig);
  }

  private Optional<Prefab.ConfigValue> getValue(String configKey) {
    return getConfig(configKey)
      .map(config -> config.getRowsList().get(0).getValues(0).getValue());
  }
}
