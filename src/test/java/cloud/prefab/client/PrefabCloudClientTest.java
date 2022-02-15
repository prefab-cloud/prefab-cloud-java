package cloud.prefab.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PrefabCloudClientTest {
  @Test
  public void testAccountId() {
    PrefabCloudClient.Builder builder = new PrefabCloudClient.Builder()
        .setNamespace("foo")
        .setApikey("50|aaaaa");


    PrefabCloudClient client = new PrefabCloudClient(builder);
    assertEquals(50, client.getProjectId());
  }
}
