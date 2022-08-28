package cloud.prefab.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class PrefabOptionsTest {

  @Test
  public void testCDNUrl() {
    PrefabCloudClient.Options options = new PrefabCloudClient.Options();

    assertThat(options.getCDNUrl())
      .isEqualTo("https://api-prefab-cloud.global.ssl.fastly.net");

    options =
      new PrefabCloudClient.Options().setPrefabApiUrl("https://api.other-server.com");

    assertThat(options.getCDNUrl())
      .isEqualTo("https://api-other-server-com.global.ssl.fastly.net");
  }
}
