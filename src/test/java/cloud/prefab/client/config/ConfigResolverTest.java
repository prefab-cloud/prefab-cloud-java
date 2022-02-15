package cloud.prefab.client.config;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigResolverTest {

  @Test
  public void test() {

    final ConfigLoader mockLoader = mock(ConfigLoader.class);

    Map<String, Prefab.ConfigDelta> data = new HashMap<>();


    put("projectA:key", "valueA", data);
    put("key", "value_none", data);
    put("projectB:key", "valueB", data);
    put("projectB.subprojectX:key", "projectB.subprojectX", data);
    put("projectB.subprojectY:key", "projectB.subprojectY", data);
    put("projectB:key2", "valueB2", data);

    when(mockLoader.calcConfig()).thenReturn(data);

    final PrefabCloudClient mockBaseClient = mock(PrefabCloudClient.class);
    when(mockBaseClient.getNamespace()).thenReturn("");
    ConfigResolver resolver = new ConfigResolver(mockBaseClient, mockLoader);


    when(mockBaseClient.getNamespace()).thenReturn("");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key"), "value_none");
    when(mockBaseClient.getNamespace()).thenReturn("projectA");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key"), "valueA");

    when(mockBaseClient.getNamespace()).thenReturn("projectB");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key"), "valueB");


    when(mockBaseClient.getNamespace()).thenReturn("projectB.subprojectX");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key"), "projectB.subprojectX");

    when(mockBaseClient.getNamespace()).thenReturn("projectB.subprojectX:subsubQ");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key"), "projectB.subprojectX");

    when(mockBaseClient.getNamespace()).thenReturn("projectC");
    resolver.update();
    assertConfigValueStringIs(resolver.getConfigValue("key"), "value_none");

    assertThat(resolver.getConfigValue("key_that_doesnt_exist").isPresent()).isFalse();
  }

  private void assertConfigValueStringIs(Optional<Prefab.ConfigValue> key, String expectedValue){
    assertThat(key.get().getString()).isEqualTo(expectedValue);
  }

  private void put(String key, String value, Map<String, Prefab.ConfigDelta> data) {
    data.put(key, Prefab.ConfigDelta.newBuilder()
        .setDefault(Prefab.ConfigValue.newBuilder()
            .setString(value).build()).build());
  }
}
