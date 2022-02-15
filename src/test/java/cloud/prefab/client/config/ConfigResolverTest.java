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

  private ConfigResolver resolver;
  private PrefabCloudClient mockBaseClient;

  @Before
  public void setup() {

    final ConfigLoader mockLoader = mock(ConfigLoader.class);

    Map<String, Prefab.ConfigDelta> data = new HashMap<>();

    when(mockLoader.calcConfig()).thenReturn(testData());
    mockBaseClient = mock(PrefabCloudClient.class);
    when(mockBaseClient.getEnvironment()).thenReturn("unspecified_env");
    when(mockBaseClient.getNamespace()).thenReturn("");
    resolver = new ConfigResolver(mockBaseClient, mockLoader);
  }


  @Test
  public void testNamespaceMatch() {
    assertThat(resolver.evaluateMatch("a.b.c", "a.b.c")).isEqualTo(new ConfigResolver.NamespaceMatch(true, 3));
    assertThat(resolver.evaluateMatch("a.b.c.d.e", "a.b.c")).isEqualTo(new ConfigResolver.NamespaceMatch(true, 3));
    assertThat(resolver.evaluateMatch("a.z.c", "a.b.c")).isEqualTo(new ConfigResolver.NamespaceMatch(false, 1));
    assertThat(resolver.evaluateMatch("", "a.b.c")).isEqualTo(new ConfigResolver.NamespaceMatch(true, 0));
    assertThat(resolver.evaluateMatch("a", "a.b")).isEqualTo(new ConfigResolver.NamespaceMatch(true, 1));
  }

  @Test
  public void test() {


    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key1"), "value_no_env_default");

    when(mockBaseClient.getEnvironment()).thenReturn("test");
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

  private void put(String key, String value, Map<String, Prefab.ConfigDelta> data) {
    data.put(key, Prefab.ConfigDelta.newBuilder()
        .setDefault(Prefab.ConfigValue.newBuilder()
            .setString(value).build()).build());
  }


  private Map<String, Prefab.ConfigDelta> testData() {
    Map<String, Prefab.ConfigDelta> rtn = Maps.newHashMap();
    rtn.put("key1", Prefab.ConfigDelta.newBuilder()
        .setKey("key1")
        .setDefault(Prefab.ConfigValue.newBuilder().setString("value_no_env_default").build())
        .addEnvs(Prefab.EnvironmentValues.newBuilder()
            .setEnvironment("test")
            .setDefault(Prefab.ConfigValue.newBuilder().setString("value_none").build())
            .addNamespaceValues(getBuild("projectA", "valueA"))
            .addNamespaceValues(getBuild("projectB", "valueB"))
            .addNamespaceValues(getBuild("projectB.subprojectX", "projectB.subprojectX"))
            .addNamespaceValues(getBuild("projectB.subprojectY", "projectB.subprojectY"))
            .build())
        .build());
    rtn.put("key2", Prefab.ConfigDelta.newBuilder()
        .setKey("key2")
        .setDefault(Prefab.ConfigValue.newBuilder().setString("valueB2").build())
        .build());
    return rtn;
  }

  private Prefab.NamespaceValue getBuild(String namespace, String value) {
    return Prefab.NamespaceValue.newBuilder()
        .setNamespace(namespace)
        .setConfigValue(Prefab.ConfigValue.newBuilder().setString(value).build())
        .build();
  }
}
