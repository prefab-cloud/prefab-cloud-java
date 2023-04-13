package cloud.prefab.client.config;

import static cloud.prefab.client.config.TestUtils.getIntConfigValue;
import static cloud.prefab.client.config.TestUtils.getStringConfigValue;
import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LookupContextTest {

  @Test
  void itPrefixesKeysWithContextType() {
    LookupContext lookupContext = new LookupContext(
      Optional.of("User"),
      Optional.of("userId101"),
      Optional.of(getStringConfigValue("coolnamespace")),
      Map.of(
        "firstname",
        getStringConfigValue("John"),
        "lastname",
        getStringConfigValue("Doe"),
        "age",
        getIntConfigValue(44)
      )
    );

    assertThat(lookupContext.getExpandedProperties())
      .isEqualTo(
        ImmutableMap
          .<String, Prefab.ConfigValue>builder()
          .put(ConfigResolver.NAMESPACE_KEY, getStringConfigValue("coolnamespace"))
          .put(ConfigResolver.LOOKUP_KEY, getStringConfigValue("userId101"))
          .put("user.firstname", getStringConfigValue("John"))
          .put("user.lastname", getStringConfigValue("Doe"))
          .put("user.age", getIntConfigValue(44))
          .build()
      );
  }

  @Test
  void itRemovesBlankType() {
    LookupContext lookupContext = new LookupContext(
      Optional.of(""),
      Optional.of("userId101"),
      Optional.of(getStringConfigValue("coolnamespace")),
      Map.of(
        "firstname",
        getStringConfigValue("John"),
        "lastname",
        getStringConfigValue("Doe"),
        "age",
        getIntConfigValue(44)
      )
    );

    assertThat(lookupContext.getExpandedProperties())
      .isEqualTo(
        ImmutableMap
          .<String, Prefab.ConfigValue>builder()
          .put(ConfigResolver.NAMESPACE_KEY, getStringConfigValue("coolnamespace"))
          .put(ConfigResolver.LOOKUP_KEY, getStringConfigValue("userId101"))
          .put("firstname", getStringConfigValue("John"))
          .put("lastname", getStringConfigValue("Doe"))
          .put("age", getIntConfigValue(44))
          .build()
      );
  }
}
