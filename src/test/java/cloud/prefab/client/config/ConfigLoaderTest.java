package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigLoaderTest {
  @Test
  public void test() {
    ConfigLoader configLoader = new ConfigLoader();
    final Map<String, Prefab.ConfigDelta> stringConfigDeltaMap = configLoader.calcConfig();

    assertThat(stringConfigDeltaMap.get("sample").getValue().getString()).isEqualTo("OneTwoThree");
    assertThat(stringConfigDeltaMap.get("sample_int").getValue().getInt()).isEqualTo(123);
    assertThat(stringConfigDeltaMap.get("sample_double").getValue().getDouble()).isEqualTo(12.12);
    assertThat(stringConfigDeltaMap.get("sample_bool").getValue().getBool()).isEqualTo(true);
    assertThat(stringConfigDeltaMap.get("sample_to_override").getValue().getString()).isEqualTo("Bar");
  }
}
