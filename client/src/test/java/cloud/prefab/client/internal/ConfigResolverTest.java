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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigResolverTest {

  @Mock
  ConfigStore configStore;

  @Mock
  ConfigRuleEvaluator configRuleEvaluator;

  @Mock
  EnvironmentVariableLookup environmentVariableLookup;

  @InjectMocks
  ConfigResolver configResolver;

  @Test
  void itLooksUpEnvvarsForProvided() {
    String key = "foo.bar.env";
    String envVarName = "COOL_ENV_VAR";
    String envVarValue = "hello, world";

    when(environmentVariableLookup.get(envVarName)).thenReturn(Optional.of(envVarValue));

    Match match = new Match(
      Prefab.ConfigValue
        .newBuilder()
        .setProvided(
          Prefab.Provided
            .newBuilder()
            .setSource(Prefab.ProvidedSource.ENV_VAR)
            .setLookup(envVarName)
        )
        .build(),
      new ConfigElement(
        Prefab.Config
          .newBuilder()
          .setKey(key)
          .setConfigType(Prefab.ConfigType.CONFIG)
          .setValueType(Prefab.Config.ValueType.STRING)
          .build(),
        new Provenance(ConfigClient.Source.LOCAL_FILE)
      ),
      Collections.emptyList(),
      0,
      0,
      Optional.empty()
    );

    when(configRuleEvaluator.getMatch(key, LookupContext.EMPTY))
      .thenReturn(Optional.of(match));

    Optional<Prefab.ConfigValue> actualConfigValue = configResolver.getConfigValue(key);

    assertThat(actualConfigValue).contains(ConfigValueUtils.from(envVarValue));
  }
}
