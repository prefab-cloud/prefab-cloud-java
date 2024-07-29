package cloud.prefab.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

public class PrefabOptionsTest {

  @Test
  public void testTelemetryDomain() {
    Options options = new Options();
    assertThat(options.getPrefabTelemetryDomain()).isEqualTo("telemetry.prefab.cloud");

    options = new Options().setPrefabDomain("staging-prefab.cloud");
    assertThat(options.getPrefabTelemetryDomain())
      .isEqualTo("telemetry.staging-prefab.cloud");
  }

  @Test
  public void testApiDomain() {
    Options options = new Options();
    assertThat(options.getPrefabApiUrl()).isEqualTo("https://cdn.prefab.cloud");
    options = new Options().setPrefabDomain("staging-prefab.cloud");
    assertThat(options.getPrefabApiUrl()).isEqualTo("https://cdn.staging-prefab.cloud");
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
