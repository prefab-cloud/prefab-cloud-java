package cloud.prefab.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import java.util.HashMap;
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

    when(mockLoader.calcConfig()).thenReturn(testData());
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

  private Map<String, Prefab.Config> testDataAddingKey3andTombstoningKey1() {
    Map<String, Prefab.Config> rtn = new HashMap<>();
    rtn.put("key1", Prefab.Config.newBuilder().setKey("key1").build());
    rtn.put("key2", key2());
    rtn.put(
      "key3",
      Prefab.Config
        .newBuilder()
        .setKey("key1")
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .setValue(Prefab.ConfigValue.newBuilder().setString("key3").build())
            .build()
        )
        .build()
    );
    return rtn;
  }

  @Test
  public void testNamespaceMatch() {
    assertThat(resolver.evaluateMatch("a.b.c", "a.b.c"))
      .isEqualTo(new ConfigResolver.NamespaceMatch(true, 3));
    assertThat(resolver.evaluateMatch("a.b.c.d.e", "a.b.c"))
      .isEqualTo(new ConfigResolver.NamespaceMatch(false, 3));
    assertThat(resolver.evaluateMatch("a.b.c", "a.b.c.d.e"))
      .isEqualTo(new ConfigResolver.NamespaceMatch(true, 3));
    assertThat(resolver.evaluateMatch("a.z.c", "a.b.c"))
      .isEqualTo(new ConfigResolver.NamespaceMatch(false, 2));
    assertThat(resolver.evaluateMatch("a", "a.b"))
      .isEqualTo(new ConfigResolver.NamespaceMatch(true, 1));
  }

  @Test
  public void testSpecialFeatureFlagBehavior() {
    final ConfigLoader mockLoader = mock(ConfigLoader.class);

    Map<String, Prefab.Config> testFFData = new HashMap<>();

    String featureName = "ff";
    testFFData.put(
      featureName,
      Prefab.Config
        .newBuilder()
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setString("v1").build())
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setString("v2").build())
        .addVariants(
          Prefab.FeatureFlagVariant.newBuilder().setString("variantInEnv").build()
        )
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .setValue(
              Prefab.ConfigValue
                .newBuilder()
                .setFeatureFlag(
                  Prefab.FeatureFlag
                    .newBuilder()
                    .setActive(true)
                    .setInactiveVariantIdx(0)
                    .addRules(
                      Prefab.Rule
                        .newBuilder()
                        .setCriteria(
                          Prefab.Criteria
                            .newBuilder()
                            .setOperator(Prefab.Criteria.CriteriaOperator.ALWAYS_TRUE)
                            .build()
                        )
                        .addVariantWeights(
                          Prefab.VariantWeight.newBuilder().setVariantIdx(1).build()
                        )
                    )
                    .build()
                )
                .build()
            )
            .build()
        )
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .setProjectEnvId(33)
            .setValue(
              Prefab.ConfigValue
                .newBuilder()
                .setFeatureFlag(
                  Prefab.FeatureFlag
                    .newBuilder()
                    .setActive(true)
                    .setInactiveVariantIdx(0)
                    .addRules(
                      Prefab.Rule
                        .newBuilder()
                        .setCriteria(
                          Prefab.Criteria
                            .newBuilder()
                            .setOperator(Prefab.Criteria.CriteriaOperator.ALWAYS_TRUE)
                            .build()
                        )
                        .addVariantWeights(
                          Prefab.VariantWeight.newBuilder().setVariantIdx(2).build()
                        )
                    )
                    .build()
                )
                .build()
            )
            .build()
        )
        .build()
    );

    when(mockLoader.calcConfig()).thenReturn(testFFData);
    mockBaseClient = mock(PrefabCloudClient.class);
    when(mockOptions.getNamespace()).thenReturn("");
    resolver = new ConfigResolver(mockBaseClient, mockLoader);
    resolver.update();

    final Optional<Prefab.ConfigValue> ff = resolver.getConfigValue(featureName);
    assertThat(ff.isPresent());
    assertThat(resolver.getConfig(featureName).get().getVariantsList()).hasSize(3);
  }

  @Test
  public void test() {
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "value_no_env_default");

    resolver.setProjectEnvId(TEST_PROJ_ENV);

    when(mockOptions.getNamespace()).thenReturn("");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "value_none");

    when(mockOptions.getNamespace()).thenReturn("projectA");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "valueA");

    when(mockOptions.getNamespace()).thenReturn("projectB");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "valueB");

    when(mockOptions.getNamespace()).thenReturn("projectB.subprojectX");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "projectB.subprojectX");

    when(mockOptions.getNamespace()).thenReturn("projectB.subprojectX.subsubQ");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "projectB.subprojectX");

    when(mockOptions.getNamespace()).thenReturn("projectC");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "value_none");

    assertThat(resolver.getConfigValue("key_that_doesnt_exist").isPresent()).isFalse();
  }

  private void assertConfigValueStringIs(
    Optional<Prefab.ConfigValue> key,
    String expectedValue
  ) {
    assertThat(key.get().getString()).isEqualTo(expectedValue);
  }

  private Map<String, Prefab.Config> testData() {
    Map<String, Prefab.Config> rtn = new HashMap<>();
    rtn.put(
      "key1",
      Prefab.Config
        .newBuilder()
        .setKey("key1")
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .setValue(
              Prefab.ConfigValue.newBuilder().setString("value_no_env_default").build()
            )
            .build()
        )
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .setProjectEnvId(TEST_PROJ_ENV)
            .setValue(Prefab.ConfigValue.newBuilder().setString("value_none").build())
            .build()
        )
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .setProjectEnvId(TEST_PROJ_ENV)
            .setNamespace("projectA")
            .setValue(Prefab.ConfigValue.newBuilder().setString("valueA").build())
            .build()
        )
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .setProjectEnvId(TEST_PROJ_ENV)
            .setNamespace("projectB")
            .setValue(Prefab.ConfigValue.newBuilder().setString("valueB").build())
            .build()
        )
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .setProjectEnvId(TEST_PROJ_ENV)
            .setNamespace("projectB.subprojectX")
            .setValue(
              Prefab.ConfigValue.newBuilder().setString("projectB.subprojectX").build()
            )
            .build()
        )
        .addRows(
          Prefab.ConfigRow
            .newBuilder()
            .setProjectEnvId(TEST_PROJ_ENV)
            .setNamespace("projectB.subprojectY")
            .setValue(
              Prefab.ConfigValue.newBuilder().setString("projectB.subprojectY").build()
            )
            .build()
        )
        .build()
    );
    rtn.put("key2", key2());
    return rtn;
  }

  private static Prefab.Config key2() {
    return Prefab.Config
      .newBuilder()
      .setKey("key2")
      .addRows(
        Prefab.ConfigRow
          .newBuilder()
          .setValue(Prefab.ConfigValue.newBuilder().setString("valueB2").build())
          .build()
      )
      .build();
  }
  //  private Prefab.NamespaceValue getBuild(String namespace, String value) {
  //    return Prefab.NamespaceValue.newBuilder()
  //        .setNamespace(namespace)
  //        .setConfigValue(Prefab.ConfigValue.newBuilder().setString(value).build())
  //        .build();
  //  }
}
