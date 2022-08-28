package cloud.prefab.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class PrefabCloudClientTest {

  @Test
  public void testApiKeyParse() {
    PrefabCloudClient.Builder builder = new PrefabCloudClient.Builder()
      .setNamespace("test.namespace")
      .setApikey("123-Development-P101-E101-SDK-23253eca-6027-46b2-9af0-2194eed793cb");

    PrefabCloudClient client = new PrefabCloudClient(builder);
    assertThat(client.getNamespace()).isEqualTo("test.namespace");
  }
}
