package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.ConfigStore;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.domain.Prefab;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yaml.snakeyaml.Yaml;

@ExtendWith(MockitoExtension.class)
class ConfigResolverTest {

  static final String ENV_VAR_NAME = "COOL_ENV_VAR";

  static final Prefab.ConfigValue PROVIDED_CV = Prefab.ConfigValue
    .newBuilder()
    .setProvided(
      Prefab.Provided
        .newBuilder()
        .setLookup(ENV_VAR_NAME)
        .setSource(Prefab.ProvidedSource.ENV_VAR)
    )
    .build();

  @Mock
  ConfigStore configStore;

  @Mock
  ConfigRuleEvaluator configRuleEvaluator;

  @Mock
  EnvironmentVariableLookup environmentVariableLookup;

  @InjectMocks
  ConfigResolver configResolver;

  @Test
  void itLooksUpEnvvarsForProvidedWithStringType() {
    String key = "foo.bar.env";
    String envVarValue = "hello, world";
    setup(key, Prefab.Config.ValueType.STRING, envVarValue);
    assertThat(configResolver.getConfigValue(key))
      .contains(ConfigValueUtils.from(envVarValue));
  }

  @Test
  void itLooksUpEnvvarsForProvidedWithIntegerType() {
    String key = "foo.bar.env";
    String envVarValue = "1234";
    long expectedValue = 1234;
    setup(key, Prefab.Config.ValueType.INT, envVarValue);
    assertThat(configResolver.getConfigValue(key))
      .contains(ConfigValueUtils.from(expectedValue));
  }

  @Test
  void itLooksUpEnvvarsForProvidedWithBooleanType() {
    String key = "foo.bar.env";
    String envVarValue = "true";
    boolean expectedValue = true;

    setup(key, Prefab.Config.ValueType.BOOL, envVarValue);
    assertThat(configResolver.getConfigValue(key))
      .contains(ConfigValueUtils.from(expectedValue));
  }

  @Test
  void itLooksUpEnvvarsForProvidedWithDoubleType() {
    String key = "foo.bar.env";
    String envVarValue = "1.101";
    double expectedValue = 1.101;

    setup(key, Prefab.Config.ValueType.DOUBLE, envVarValue);
    assertThat(configResolver.getConfigValue(key))
      .contains(ConfigValueUtils.from(expectedValue));
  }

  @Test
  void itLooksUpEnvvarsForProvidedWithStringListType() {
    String key = "foo.bar.env";
    String envVarValue = "[a,b,c]";
    List<String> expectedValue = List.of("a", "b", "c");
    setup(key, Prefab.Config.ValueType.STRING_LIST, envVarValue);
    assertThat(configResolver.getConfigValue(key))
      .contains(ConfigValueUtils.from(expectedValue));
  }

  private void setup(String key, Prefab.Config.ValueType valueType, String envVarValue) {
    when(environmentVariableLookup.get(ENV_VAR_NAME))
      .thenReturn(Optional.of(envVarValue));

    when(configRuleEvaluator.getMatch(key, LookupContext.EMPTY))
      .thenReturn(Optional.of(match(PROVIDED_CV, configWithValueType(key, valueType))));
  }

  Match match(Prefab.ConfigValue configValue, Prefab.Config config) {
    return new Match(
      configValue,
      new ConfigElement(config, new Provenance(ConfigClient.Source.LOCAL_FILE)),
      Collections.emptyList(),
      0,
      0,
      Optional.empty()
    );
  }

  Prefab.Config configWithValueType(String key, Prefab.Config.ValueType valueType) {
    return Prefab.Config
      .newBuilder()
      .setKey(key)
      .setConfigType(Prefab.ConfigType.CONFIG)
      .setValueType(valueType)
      .build();
  }
}
