package cloud.prefab.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PrefabCloudClientTest {
  @Test
  public void testApiKeyParse() {
    PrefabCloudClient.Builder builder = new PrefabCloudClient.Builder()
        .setNamespace("test.namespace")
        .setApikey("50-test-test_api_key");


    PrefabCloudClient client = new PrefabCloudClient(builder);
    assertEquals(50, client.getProjectId());
    assertEquals("test", client.getEnvironment());
    assertEquals("test.namespace", client.getNamespace());
  }
}
