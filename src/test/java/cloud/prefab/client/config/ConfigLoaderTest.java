package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigLoaderTest {
  @Test
  public void testLoad() {
    ConfigLoader configLoader = new ConfigLoader();
    final Map<String, Prefab.ConfigDelta> stringConfigDeltaMap = configLoader.calcConfig();

    assertThat(stringConfigDeltaMap.get("sample").getDefault().getString()).isEqualTo("OneTwoThree");
    assertThat(stringConfigDeltaMap.get("sample_int").getDefault().getInt()).isEqualTo(123);
    assertThat(stringConfigDeltaMap.get("sample_double").getDefault().getDouble()).isEqualTo(12.12);
    assertThat(stringConfigDeltaMap.get("sample_bool").getDefault().getBool()).isEqualTo(true);
    assertThat(stringConfigDeltaMap.get("sample_to_override").getDefault().getString()).isEqualTo("Bar");
  }


  @Test
  public void testHighwater() {
    ConfigLoader configLoader = new ConfigLoader();
    assertThat(configLoader.getHighwaterMark()).isEqualTo(0);

    configLoader.set(cd(1, "sample_int", 456));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(1);

    configLoader.set(cd(5, "sample_int", 456));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(5);

    configLoader.set(cd(3, "sample_int", 456));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(5);

  }

  @Test
  public void testKeepsMostRecent() {
    ConfigLoader configLoader = new ConfigLoader();
    assertThat(configLoader.getHighwaterMark()).isEqualTo(0);

    configLoader.set(cd(1, "sample_int", 1));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(1);
    assertThat(configLoader.calcConfig().get("sample_int").getDefault().getInt()).isEqualTo(1);

    configLoader.set(cd(4, "sample_int", 4));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(4);
    assertThat(configLoader.calcConfig().get("sample_int").getDefault().getInt()).isEqualTo(4);

    configLoader.set(cd(2, "sample_int", 2));
    assertThat(configLoader.getHighwaterMark()).isEqualTo(4);
    assertThat(configLoader.calcConfig().get("sample_int").getDefault().getInt()).isEqualTo(4);
  }


  @Test
  public void testAPIPrecedence() {
    ConfigLoader configLoader = new ConfigLoader();
    configLoader.calcConfig();

    assertThat(configLoader.calcConfig().get("sample_int").getDefault().getInt()).isEqualTo(123);
    configLoader.set(cd(2, "sample_int", 456));
    assertThat(configLoader.calcConfig().get("sample_int").getDefault().getInt()).isEqualTo(456);

  }

  @Test
  public void testLoadingTombstonesRemoves() {
    ConfigLoader configLoader = new ConfigLoader();
    assertThat(configLoader.calcConfig().get("val_from_api")).isNull();

    configLoader.set(cd(2, "val_from_api", 456));
    assertThat(configLoader.calcConfig().get("val_from_api").getDefault().getInt()).isEqualTo(456);

    configLoader.set(Prefab.ConfigDelta.newBuilder()
        .setId(2)
        .setKey("val_from_api").build());
    assertThat(configLoader.calcConfig().get("val_from_api")).isNull();
  }

  private Prefab.ConfigDelta cd(int id, String key, int val) {
    return Prefab.ConfigDelta.newBuilder()
        .setId(id)
        .setKey(key)
        .setDefault(Prefab.ConfigValue.newBuilder().setInt(val).build()).build();
  }

}
