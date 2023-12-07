package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.ConfigStore;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.client.exceptions.ConfigValueDecryptionException;
import cloud.prefab.client.exceptions.ConfigValueException;
import cloud.prefab.domain.Prefab;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

  @Nested
  class EnvVarTests {

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

    private void setup(
      String key,
      Prefab.Config.ValueType valueType,
      String envVarValue
    ) {
      when(environmentVariableLookup.get(ENV_VAR_NAME))
        .thenReturn(Optional.of(envVarValue));

      when(configRuleEvaluator.getMatch(key, LookupContext.EMPTY))
        .thenReturn(Optional.of(match(PROVIDED_CV, configWithValueType(key, valueType))));
    }
  }

  @Nested
  class EncryptedValueTests {

    @Test
    void itHandlesSecretValues() {
      String secretValueConfigKey = "the.secret.value";
      String encryptionKeyConfigKey = "the.secret.key";
      String secretValue =
        "b837acfdedb9f6286947fb95f6fb--13490148d8d3ddf0decc3d14--add9b0ed6de775080bec4c5b6025d67e";
      String encryptionKey =
        "e657e0406fc22e17d3145966396b2130d33dcb30ac0edd62a77235cdd01fc49d";

      when(configRuleEvaluator.getMatch(secretValueConfigKey, LookupContext.EMPTY))
        .thenReturn(
          Optional.of(
            match(
              Prefab.ConfigValue
                .newBuilder()
                .setString(secretValue)
                .setDecryptWith(encryptionKeyConfigKey)
                .build(),
              Prefab.Config.newBuilder().setKey(secretValueConfigKey).build()
            )
          )
        );

      when(configRuleEvaluator.getMatch(encryptionKeyConfigKey, LookupContext.EMPTY))
        .thenReturn(
          Optional.of(
            match(
              Prefab.ConfigValue.newBuilder().setString(encryptionKey).build(),
              Prefab.Config.newBuilder().setKey(encryptionKeyConfigKey).build()
            )
          )
        );

      Optional<Match> decryptedMatchMaybe = configResolver.getMatch(
        secretValueConfigKey,
        LookupContext.EMPTY
      );
      assertThat(decryptedMatchMaybe).isPresent();
      assertThat(decryptedMatchMaybe.get().getConfigValue().getString())
        .isEqualTo("james-was-here");
    }

    @Test
    void itThrowsWrappedExceptionWhenDecryptionFails() {
      String secretValueConfigKey = "the.secret.value";
      String encryptionKeyConfigKey = "the.secret.key";
      String secretValue =
        "b837acfdedb9f6286947fb95f6fb--13490148d8d3ddf0decc3d14--add9b0ed6de775080bec4c5b6025d67e";
      String encryptionKey = "not a valid key";

      when(configRuleEvaluator.getMatch(secretValueConfigKey, LookupContext.EMPTY))
        .thenReturn(
          Optional.of(
            match(
              Prefab.ConfigValue
                .newBuilder()
                .setString(secretValue)
                .setDecryptWith(encryptionKeyConfigKey)
                .build(),
              Prefab.Config.newBuilder().setKey(secretValueConfigKey).build()
            )
          )
        );

      when(configRuleEvaluator.getMatch(encryptionKeyConfigKey, LookupContext.EMPTY))
        .thenReturn(
          Optional.of(
            match(
              Prefab.ConfigValue.newBuilder().setString(encryptionKey).build(),
              Prefab.Config.newBuilder().setKey(encryptionKeyConfigKey).build()
            )
          )
        );

      assertThatThrownBy(() ->
          configResolver.getMatch(secretValueConfigKey, LookupContext.EMPTY)
        )
        .isInstanceOf(ConfigValueDecryptionException.class)
        .hasMessageContaining(encryptionKeyConfigKey);
    }

    @Test
    void itThrowsWrappedExceptionWhenDecryptWithConfigDoesNotExist() {
      String secretValueConfigKey = "the.secret.value";
      String encryptionKeyConfigKey = "the.secret.key";
      String secretValue =
        "b837acfdedb9f6286947fb95f6fb--13490148d8d3ddf0decc3d14--add9b0ed6de775080bec4c5b6025d67e";
      String encryptionKey = "not a valid key";

      when(configRuleEvaluator.getMatch(secretValueConfigKey, LookupContext.EMPTY))
        .thenReturn(
          Optional.of(
            match(
              Prefab.ConfigValue
                .newBuilder()
                .setString(secretValue)
                .setDecryptWith(encryptionKeyConfigKey)
                .build(),
              Prefab.Config.newBuilder().setKey(secretValueConfigKey).build()
            )
          )
        );

      when(configRuleEvaluator.getMatch(encryptionKeyConfigKey, LookupContext.EMPTY))
        .thenReturn(Optional.empty());

      assertThatThrownBy(() ->
          configResolver.getMatch(secretValueConfigKey, LookupContext.EMPTY)
        )
        .isInstanceOf(ConfigValueException.class)
        .hasMessageContaining(encryptionKeyConfigKey);
    }
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
