package cloud.prefab.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

public class PrefabOptionsTest {

  @Test
  public void testCDNUrl() {
    Options options = new Options();

    assertThat(options.getCDNUrl())
      .isEqualTo("https://api-prefab-cloud.global.ssl.fastly.net");

    options =
      new Options().setPrefabApiUrl("https://api.other-server.com");

    assertThat(options.getCDNUrl())
      .isEqualTo("https://api-other-server-com.global.ssl.fastly.net");
  }

  @Test
  public void testPrefabEnvs() {
    Options options = new Options();

    assertThat(options.getAllPrefabEnvs()).isEqualTo(List.of("default"));

    options =
      new Options().setPrefabEnvs(List.of("development", "jeff"));

    assertThat(options.getAllPrefabEnvs())
      .isEqualTo(List.of("default", "development", "jeff"));
  }
}
