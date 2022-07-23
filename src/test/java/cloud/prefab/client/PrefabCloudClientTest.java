package cloud.prefab.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class PrefabCloudClientTest {

  @Test
  public void testApiKeyParse() {
    PrefabCloudClient.Builder builder = new PrefabCloudClient.Builder()
      .setNamespace("test.namespace")
      .setApikey("50-test-test_api_key");

    PrefabCloudClient client = new PrefabCloudClient(builder);
    assertThat(client.getProjectId()).isEqualTo(50);
    assertThat(client.getEnvironment()).isEqualTo("test");
    assertThat(client.getNamespace()).isEqualTo("test.namespace");
  }
}
