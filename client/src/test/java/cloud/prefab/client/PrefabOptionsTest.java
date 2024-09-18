package cloud.prefab.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

public class PrefabOptionsTest {

  @Test
  public void testTelemetryDomain() {
    Options options = new Options();
    assertThat(options.getPrefabTelemetryHost())
      .isEqualTo("https://telemetry.prefab.cloud");

    options = new Options().setPrefabTelemetryHost("http://staging-prefab.cloud");
    assertThat(options.getPrefabTelemetryHost()).isEqualTo("http://staging-prefab.cloud");
  }

  @Test
  public void testApiDomain() {
    Options options = new Options();
    assertThat(options.getApiHosts()).isEqualTo(Options.DEFAULT_API_HOSTS);
    options = new Options().setApiHosts(List.of("staging-prefab.cloud"));
    assertThat(options.getApiHosts()).isEqualTo(List.of("https://staging-prefab.cloud"));
  }

  @Test
  public void testStreamDomain() {
    Options options = new Options();
    assertThat(options.getStreamHosts()).isEqualTo(Options.DEFAULT_STREAM_HOSTS);
    options = new Options().setApiHosts(List.of("stream.staging-prefab.cloud"));
    assertThat(options.getApiHosts())
      .isEqualTo(List.of("https://stream.staging-prefab.cloud"));
  }

  @Test
  public void testPrefabEnvs() {
    Options options = new Options();

    assertThat(options.getAllPrefabEnvs()).isEqualTo(List.of("default"));

    options = new Options().setPrefabEnvs(List.of("development", "jeff"));

    assertThat(options.getAllPrefabEnvs())
      .isEqualTo(List.of("default", "development", "jeff"));
  }
}
