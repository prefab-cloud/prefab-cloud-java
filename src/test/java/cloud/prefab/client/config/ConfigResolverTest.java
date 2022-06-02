package cloud.prefab.client.config;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigResolverTest {

  private final int TEST_PROJ_ENV = 2;

  private ConfigResolver resolver;
  private PrefabCloudClient mockBaseClient;

  @Before
  public void setup() {

    final ConfigLoader mockLoader = mock(ConfigLoader.class);

    when(mockLoader.calcConfig()).thenReturn(testData());
    mockBaseClient = mock(PrefabCloudClient.class);
    when(mockBaseClient.getEnvironment()).thenReturn("unspecified_env");
    when(mockBaseClient.getNamespace()).thenReturn("");
    resolver = new ConfigResolver(mockBaseClient, mockLoader);
  }


  @Test
  public void testNamespaceMatch() {
    assertThat(resolver.evaluateMatch("a.b.c", "a.b.c")).isEqualTo(new ConfigResolver.NamespaceMatch(true, 3));
    assertThat(resolver.evaluateMatch("a.b.c.d.e", "a.b.c")).isEqualTo(new ConfigResolver.NamespaceMatch(false, 3));
    assertThat(resolver.evaluateMatch("a.b.c", "a.b.c.d.e")).isEqualTo(new ConfigResolver.NamespaceMatch(true, 3));
    assertThat(resolver.evaluateMatch("a.z.c", "a.b.c")).isEqualTo(new ConfigResolver.NamespaceMatch(false, 2));
    assertThat(resolver.evaluateMatch("a", "a.b")).isEqualTo(new ConfigResolver.NamespaceMatch(true, 1));
  }

  @Test
  public void testSpecialFeatureFlagBehavior() {
    final ConfigLoader mockLoader = mock(ConfigLoader.class);

    Map<String, Prefab.Config> testFFData = Maps.newHashMap();

    String featureName = "ff";
    testFFData.put(featureName, Prefab.Config.newBuilder()
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setString("v1").build())
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setString("v2").build())
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setString("variantInEnv").build())
        .addRows(Prefab.ConfigRow.newBuilder().setValue(Prefab.ConfigValue.newBuilder()
                .setFeatureFlag(Prefab.FeatureFlag.newBuilder()
                    .setActive(true)
                    .setInactiveVariantIdx(0)
                    .addRules(Prefab.Rule.newBuilder()
                        .setCriteria(Prefab.Criteria.newBuilder().setOperator(Prefab.Criteria.CriteriaOperator.ALWAYS_TRUE).build())
                        .addVariantWeights(Prefab.VariantWeight.newBuilder().setVariantIdx(1).build())
                    )
                    .build())
                .build())
            .build())
        .addRows(Prefab.ConfigRow.newBuilder()
            .setProjectEnvId(33)
            .setValue(Prefab.ConfigValue.newBuilder()
                .setFeatureFlag(Prefab.FeatureFlag.newBuilder()
                    .setActive(true)
                    .setInactiveVariantIdx(0)
                    .addRules(Prefab.Rule.newBuilder()
                        .setCriteria(Prefab.Criteria.newBuilder().setOperator(Prefab.Criteria.CriteriaOperator.ALWAYS_TRUE).build())
                        .addVariantWeights(Prefab.VariantWeight.newBuilder().setVariantIdx(2).build())
                    )
                    .build())
                .build())
            .build())
        .build());


    when(mockLoader.calcConfig()).thenReturn(testFFData);
    mockBaseClient = mock(PrefabCloudClient.class);
    when(mockBaseClient.getEnvironment()).thenReturn("test");
    when(mockBaseClient.getNamespace()).thenReturn("");
    resolver = new ConfigResolver(mockBaseClient, mockLoader);


    final Optional<Prefab.ConfigValue> ff = resolver.getConfigValue(featureName);
    assertThat(ff.isPresent());
    assertThat(resolver.getConfig(featureName).get().getVariantsList()).hasSize(3);
  }

  @Test
  public void test() {


    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "value_no_env_default");

    resolver.setProjectEnvId(TEST_PROJ_ENV);

    when(mockBaseClient.getNamespace()).thenReturn("");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "value_none");

    when(mockBaseClient.getNamespace()).thenReturn("projectA");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "valueA");

    when(mockBaseClient.getNamespace()).thenReturn("projectB");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "valueB");


    when(mockBaseClient.getNamespace()).thenReturn("projectB.subprojectX");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "projectB.subprojectX");

    when(mockBaseClient.getNamespace()).thenReturn("projectB.subprojectX.subsubQ");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "projectB.subprojectX");

    when(mockBaseClient.getNamespace()).thenReturn("projectC");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "value_none");

    assertThat(resolver.getConfigValue("key_that_doesnt_exist").isPresent()).isFalse();
  }

  private void assertConfigValueStringIs(Optional<Prefab.ConfigValue> key, String expectedValue) {
    assertThat(key.get().getString()).isEqualTo(expectedValue);
  }

  private void put(String key, String value, Map<String, Prefab.Config> data) {
    data.put(key, Prefab.Config.newBuilder()
        .setKey(key)
        .addRows(Prefab.ConfigRow.newBuilder().setValue(Prefab.ConfigValue.newBuilder().setString(value).build())
        ).build());

  }


  private Map<String, Prefab.Config> testData() {
    Map<String, Prefab.Config> rtn = Maps.newHashMap();
    rtn.put("key1", Prefab.Config.newBuilder()
        .setKey("key1")
        .addRows(Prefab.ConfigRow.newBuilder()
            .setValue(Prefab.ConfigValue.newBuilder().setString("value_no_env_default").build()).build())
        .addRows(Prefab.ConfigRow.newBuilder().setProjectEnvId(TEST_PROJ_ENV)
            .setValue(Prefab.ConfigValue.newBuilder().setString("value_none").build()).build())
        .addRows(Prefab.ConfigRow.newBuilder().setProjectEnvId(TEST_PROJ_ENV)
            .setNamespace("projectA")
            .setValue(Prefab.ConfigValue.newBuilder().setString("valueA").build()).build())
        .addRows(Prefab.ConfigRow.newBuilder().setProjectEnvId(TEST_PROJ_ENV)
            .setNamespace("projectB")
            .setValue(Prefab.ConfigValue.newBuilder().setString("valueB").build()).build())
        .addRows(Prefab.ConfigRow.newBuilder().setProjectEnvId(TEST_PROJ_ENV)
            .setNamespace("projectB.subprojectX")
            .setValue(Prefab.ConfigValue.newBuilder().setString("projectB.subprojectX").build()).build())
        .addRows(Prefab.ConfigRow.newBuilder().setProjectEnvId(TEST_PROJ_ENV)
            .setNamespace("projectB.subprojectY")
            .setValue(Prefab.ConfigValue.newBuilder().setString("projectB.subprojectY").build()).build())

        .build());
    rtn.put("key2", Prefab.Config.newBuilder()
        .setKey("key2")
        .addRows(Prefab.ConfigRow.newBuilder()
            .setValue(Prefab.ConfigValue.newBuilder().setString("valueB2").build()).build())
        .build());
    return rtn;
  }

//  private Prefab.NamespaceValue getBuild(String namespace, String value) {
//    return Prefab.NamespaceValue.newBuilder()
//        .setNamespace(namespace)
//        .setConfigValue(Prefab.ConfigValue.newBuilder().setString(value).build())
//        .build();
//  }
}
