package cloud.prefab.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.domain.Prefab;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigValueUtilsTest {

  @Test
  void itBuildsCorrectMap() {
    assertThat(ConfigValueUtils.fromStringMap(Map.of("hello", "1", "world", "2")))
      .isEqualTo(
        Map.of(
          "hello",
          ConfigValueUtils.fromString("1"),
          "world",
          ConfigValueUtils.fromString("2")
        )
      );
  }

  @Test
  void itBuildsCorrectConfigFromString() {
    assertThat(ConfigValueUtils.fromString("hello"))
      .isEqualTo(Prefab.ConfigValue.newBuilder().setString("hello").build());
  }
}
