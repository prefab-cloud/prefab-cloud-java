package cloud.prefab.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigResolverTest {

  private final int TEST_PROJ_ENV = 2;

  private ConfigResolver resolver;
  private PrefabCloudClient mockBaseClient;
  private Options mockOptions;
  private ConfigLoader mockLoader;

  @BeforeEach
  public void setup() {
    mockLoader = mock(ConfigLoader.class);
    mockOptions = mock(Options.class);

    when(mockLoader.calcConfig()).thenReturn(testData(false));
    mockBaseClient = mock(PrefabCloudClient.class);
    when(mockBaseClient.getOptions()).thenReturn(mockOptions);
    resolver = new ConfigResolver(mockBaseClient, mockLoader);
  }

  @Test
  public void testUpdateChangeDetection() {
    // original testData() has 2 keys
    assertThat(resolver.update())
      .containsExactlyInAnyOrder(
        new ConfigChangeEvent(
          "key1",
          Optional.empty(),
          Optional.of(
            Prefab.ConfigValue.newBuilder().setString("value_no_env_default").build()
          )
        ),
        new ConfigChangeEvent(
          "key2",
          Optional.empty(),
          Optional.of(Prefab.ConfigValue.newBuilder().setString("valueB2").build())
        )
      );

    when(mockLoader.calcConfig()).thenReturn(testDataAddingKey3andTombstoningKey1());
    assertThat(resolver.update())
      .containsExactlyInAnyOrder(
        new ConfigChangeEvent(
          "key1",
          Optional.of(
            Prefab.ConfigValue.newBuilder().setString("value_no_env_default").build()
          ),
          Optional.empty()
        ),
        new ConfigChangeEvent(
          "key3",
          Optional.empty(),
          Optional.of(Prefab.ConfigValue.newBuilder().setString("key3").build())
        )
      );
  }

  private Map<String, ConfigElement> testDataAddingKey3andTombstoningKey1() {
    Map<String, ConfigElement> rtn = new HashMap<>();
    rtn.put("key1", ce(Prefab.Config.newBuilder().setKey("key1").build()));
    rtn.put("key2", ce(key2()));
    rtn.put(
      "key3",
      ce(
        Prefab.Config
          .newBuilder()
          .setKey("key1")
          .addRows(rowWithStringValue("key3"))
          .build()
      )
    );
    return rtn;
  }

  private Prefab.ConfigRow rowWithStringValue(String value) {
    return rowWithStringValue(value, Optional.empty(), Optional.empty());
  }

  private Prefab.ConfigRow rowWithStringValue(
    String value,
    Optional<Integer> env,
    Optional<Map<String, String>> namespaceValues
  ) {
    final Prefab.ConfigRow.Builder rowBuilder = Prefab.ConfigRow.newBuilder();
    if (env.isPresent()) {
      rowBuilder.setProjectEnvId(env.get());
    }
    if (namespaceValues.isPresent()) {
      namespaceValues
        .get()
        .forEach((namespace, stringValue) -> {
          final Prefab.ConditionalValue.Builder builder = Prefab.ConditionalValue
            .newBuilder()
            .setValue(Prefab.ConfigValue.newBuilder().setString(stringValue).build());

          if (namespace != null) {
            builder.addCriteria(
              Prefab.Criterion
                .newBuilder()
                .setPropertyName(ConfigResolver.NAMESPACE_KEY)
                .setOperator(Prefab.Criterion.CriteriaOperator.HIERARCHICAL_MATCH)
                .setValueToMatch(Prefab.ConfigValue.newBuilder().setString(namespace))
            );
          }
          rowBuilder.addValues(builder.build());
        });
    }
    rowBuilder
      .addValues(
        Prefab.ConditionalValue
          .newBuilder()
          .setValue(Prefab.ConfigValue.newBuilder().setString(value))
          .build()
      )
      .build();
    return rowBuilder.build();
  }

  @Test
  public void testNamespaceMatch() {
    assertThat(resolver.hierarchicalMatch("a.b.c", "a.b.c")).isEqualTo(true);
    assertThat(resolver.hierarchicalMatch("a.b.c", "a.b.c.d.e")).isEqualTo(false);
    assertThat(resolver.hierarchicalMatch("a.b.c.d.e", "a.b.c")).isEqualTo(true);
    assertThat(resolver.hierarchicalMatch("a.z.c", "a.b.c")).isEqualTo(false);
    assertThat(resolver.hierarchicalMatch("a.b", "a")).isEqualTo(true);
  }

  private Prefab.Configs configsWithEnv(int projectEnvId) {
    return Prefab.Configs
      .newBuilder()
      .setConfigServicePointer(
        Prefab.ConfigServicePointer.newBuilder().setProjectEnvId(projectEnvId).build()
      )
      .build();
  }

  @Test
  public void noProjEnvOverwrite() {
    assertThat(resolver.setProjectEnvId(configsWithEnv(1))).isTrue();
    assertThat(resolver.setProjectEnvId(Prefab.Configs.getDefaultInstance())).isFalse();
  }

  @Test
  public void test() {
    resolver.update();
    resolver.contentsString();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "value_no_env_default");

    resolver.setProjectEnvId(configsWithEnv(TEST_PROJ_ENV));
    resolver.contentsString();

    when(mockOptions.getNamespace()).thenReturn(Optional.empty());
    resolver.update();
    resolver.contentsString();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "value_none");

    when(mockOptions.getNamespace()).thenReturn(Optional.of("projectA"));
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "valueA");

    when(mockOptions.getNamespace()).thenReturn(Optional.of("projectB"));
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "valueB");

    when(mockOptions.getNamespace()).thenReturn(Optional.of("projectB.subprojectX"));
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "projectB.subprojectX");

    when(mockOptions.getNamespace())
      .thenReturn(Optional.of("projectB.subprojectX.subsubQ"));
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "projectB.subprojectX");

    when(mockOptions.getNamespace()).thenReturn(Optional.of("projectC"));
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "value_none");

    assertThat(resolver.getConfigValue("key_that_doesnt_exist").isPresent()).isFalse();
  }

  @Test
  public void testContentsString() {
    resolver.update();
    String expected =
      "\n" +
      "key1                          value_no_env_default                    LOCAL_ONLY:unit test                                                            \n" +
      "key2                          valueB2                                 LOCAL_ONLY:unit test                                                            \n";
    assertThat(resolver.contentsString()).isEqualTo(expected);
  }

  private void assertConfigValueStringIs(
    Optional<Prefab.ConfigValue> key,
    String expectedValue
  ) {
    assertThat(key.get().getString()).isEqualTo(expectedValue);
  }

  private Map<String, ConfigElement> testData(boolean segment) {
    Map<String, ConfigElement> rtn = new HashMap<>();
    rtn.put("key1", ce(key1()));
    rtn.put("key2", ce(key2()));
    if (segment) {
      rtn.put("segment", ce(segmentTestData()));
    }
    return rtn;
  }

  private Prefab.Config segmentTestData() {
    return Prefab.Config
      .newBuilder()
      .setKey("segment")
      .addRows(
        Prefab.ConfigRow
          .newBuilder()
          .addValues(
            Prefab.ConditionalValue
              .newBuilder()
              .setValue(
                Prefab.ConfigValue
                  .newBuilder()
                  .setSegment(
                    Prefab.Segment
                      .newBuilder()
                      .addCriteria(
                        Prefab.Criterion
                          .newBuilder()
                          .setPropertyName("group")
                          .setOperator(Prefab.Criterion.CriteriaOperator.PROP_IS_ONE_OF)
                          .setValueToMatch(
                            Prefab.ConfigValue
                              .newBuilder()
                              .setStringList(
                                Prefab.StringList.newBuilder().addValues("beta").build()
                              )
                              .build()
                          )
                      )
                      .build()
                  )
              )
              .build()
          )
          .build()
      )
      .build();
  }

  private ConfigElement ce(Prefab.Config config) {
    return new ConfigElement(
      config,
      new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit test")
    );
  }

  private Prefab.Config key1() {
    LinkedHashMap<String, String> map = new LinkedHashMap(); // insertion order important
    map.put("projectB.subprojectY", "projectB.subprojectY");
    map.put("projectB.subprojectX", "projectB.subprojectX");
    map.put("projectA", "valueA");
    map.put("projectB", "valueB");
    map.put(null, "value_none");

    return Prefab.Config
      .newBuilder()
      .setKey("key1")
      .addRows(rowWithStringValue("value_no_env_default"))
      .addRows(
        rowWithStringValue("value_none", Optional.of(TEST_PROJ_ENV), Optional.of(map))
      ) // order important here
      .build();
  }

  private Prefab.Config key2() {
    return Prefab.Config
      .newBuilder()
      .setKey("key2")
      .addRows(rowWithStringValue("valueB2"))
      .build();
  }

  @Test
  public void testPct() {
    Prefab.Config flag = getTrueFalseConfig(500, 500);

    assertThat(
      resolver
        .findMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit test")
          ),
          Map.of(
            ConfigResolver.LOOKUP_KEY,
            Prefab.ConfigValue.newBuilder().setString("very high hash").build()
          )
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(false).build());

    assertThat(
      resolver
        .findMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit test")
          ),
          Map.of(
            ConfigResolver.LOOKUP_KEY,
            Prefab.ConfigValue.newBuilder().setString("hashes low").build()
          )
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(true).build());
  }

  @Test
  public void testOff() {
    Prefab.Config flag = getTrueFalseConfig(0, 1000);

    assertThat(
      resolver
        .findMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit test")
          ),
          Map.of(
            ConfigResolver.LOOKUP_KEY,
            Prefab.ConfigValue.newBuilder().setString("very high hash").build()
          )
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(false).build());

    assertThat(
      resolver
        .findMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit test")
          ),
          Map.of(
            ConfigResolver.LOOKUP_KEY,
            Prefab.ConfigValue.newBuilder().setString("hashes low").build()
          )
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(false).build());
  }

  @Test
  public void testOn() {
    Prefab.Config flag = getTrueFalseConfig(1000, 0);

    assertThat(
      resolver
        .findMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit test")
          ),
          Map.of(
            ConfigResolver.LOOKUP_KEY,
            Prefab.ConfigValue.newBuilder().setString("very high hash").build()
          )
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(true).build());

    assertThat(
      resolver
        .findMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit test")
          ),
          Map.of(
            ConfigResolver.LOOKUP_KEY,
            Prefab.ConfigValue.newBuilder().setString("hashes low").build()
          )
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(true).build());
  }

  private static Prefab.Config getTrueFalseConfig(int trueWeight, int falseWeight) {
    Prefab.WeightedValues wvs = Prefab.WeightedValues
      .newBuilder()
      .addWeightedValues(
        Prefab.WeightedValue
          .newBuilder()
          .setWeight(trueWeight)
          .setValue(Prefab.ConfigValue.newBuilder().setBool(true).build())
          .build()
      )
      .addWeightedValues(
        Prefab.WeightedValue
          .newBuilder()
          .setWeight(falseWeight)
          .setValue(Prefab.ConfigValue.newBuilder().setBool(false).build())
          .build()
      )
      .build();

    Prefab.Config flag = Prefab.Config
      .newBuilder()
      .setKey("FlagName")
      .addAllowableValues(Prefab.ConfigValue.newBuilder().setBool(true))
      .addAllowableValues(Prefab.ConfigValue.newBuilder().setBool(false))
      .addRows(
        Prefab.ConfigRow
          .newBuilder()
          .addValues(
            Prefab.ConditionalValue
              .newBuilder()
              .setValue(Prefab.ConfigValue.newBuilder().setWeightedValues(wvs))
              .build()
          )
      )
      .build();
    return flag;
  }

  @Test
  public void testEndsWith() {
    final Prefab.Criterion socialEmailCritieria = Prefab.Criterion
      .newBuilder()
      .setPropertyName("email")
      .setValueToMatch(
        Prefab.ConfigValue
          .newBuilder()
          .setStringList(
            Prefab.StringList
              .newBuilder()
              .addValues("gmail.com")
              .addValues("yahoo.com")
              .build()
          )
      )
      .setOperator(Prefab.Criterion.CriteriaOperator.PROP_ENDS_WITH_ONE_OF)
      .build();

    final EvaluatedCriterion bobEval = resolver.evaluateCriterionMatch(
      socialEmailCritieria,
      ImmutableMap.of("email", sv("bob@example.com"))
    );
    assertThat(bobEval.isMatch()).isFalse();
    assertThat(bobEval.getEvaluatedProperty().get().getString())
      .isEqualTo("bob@example.com");

    final EvaluatedCriterion yahooEval = resolver.evaluateCriterionMatch(
      socialEmailCritieria,
      ImmutableMap.of("email", sv("alice@yahoo.com"))
    );
    assertThat(yahooEval.isMatch()).isTrue();
    assertThat(yahooEval.getEvaluatedProperty().get().getString())
      .isEqualTo("alice@yahoo.com");

    final EvaluatedCriterion gmailEval = resolver.evaluateCriterionMatch(
      socialEmailCritieria,
      ImmutableMap.of("email", sv("alice@gmail.com"))
    );
    assertThat(gmailEval.isMatch()).isTrue();
    assertThat(gmailEval.getEvaluatedProperty().get().getString())
      .isEqualTo("alice@gmail.com");
  }

  @Test
  public void testDoesNotEndWith() {
    final Prefab.Criterion socialEmailCritieria = Prefab.Criterion
      .newBuilder()
      .setPropertyName("email")
      .setValueToMatch(
        Prefab.ConfigValue
          .newBuilder()
          .setStringList(
            Prefab.StringList
              .newBuilder()
              .addValues("gmail.com")
              .addValues("yahoo.com")
              .build()
          )
      )
      .setOperator(Prefab.Criterion.CriteriaOperator.PROP_DOES_NOT_END_WITH_ONE_OF)
      .build();

    final EvaluatedCriterion bobEval = resolver.evaluateCriterionMatch(
      socialEmailCritieria,
      ImmutableMap.of("email", sv("bob@example.com"))
    );
    assertThat(bobEval.isMatch()).isTrue();
    assertThat(bobEval.getEvaluatedProperty().get().getString())
      .isEqualTo("bob@example.com");

    final EvaluatedCriterion yahooEval = resolver.evaluateCriterionMatch(
      socialEmailCritieria,
      ImmutableMap.of("email", sv("alice@yahoo.com"))
    );
    assertThat(yahooEval.isMatch()).isFalse();
    assertThat(yahooEval.getEvaluatedProperty().get().getString())
      .isEqualTo("alice@yahoo.com");

    final EvaluatedCriterion gmailEval = resolver.evaluateCriterionMatch(
      socialEmailCritieria,
      ImmutableMap.of("email", sv("alice@gmail.com"))
    );
    assertThat(gmailEval.isMatch()).isFalse();
    assertThat(gmailEval.getEvaluatedProperty().get().getString())
      .isEqualTo("alice@gmail.com");
  }

  @Test
  public void testSegment() {
    when(mockLoader.calcConfig()).thenReturn(testData(true));
    resolver.update();

    final Prefab.Criterion segmentCriteria = Prefab.Criterion
      .newBuilder()
      .setValueToMatch(Prefab.ConfigValue.newBuilder().setString("segment").build())
      .setOperator(Prefab.Criterion.CriteriaOperator.IN_SEG)
      .build();
    final EvaluatedCriterion betaEval = resolver.evaluateCriterionMatch(
      segmentCriteria,
      ImmutableMap.of("group", sv("beta"))
    );
    assertThat(betaEval.getEvaluatedProperty().get().getString()).isEqualTo("beta");
    assertThat(betaEval.isMatch()).isTrue();

    final EvaluatedCriterion alphaEval = resolver.evaluateCriterionMatch(
      segmentCriteria,
      ImmutableMap.of("group", sv("alpha"))
    );
    assertThat(alphaEval.isMatch()).isFalse();
    assertThat(alphaEval.getEvaluatedProperty().get().getString()).isEqualTo("alpha");
  }

  private Prefab.ConfigValue sv(String s) {
    return Prefab.ConfigValue.newBuilder().setString(s).build();
  }
}
