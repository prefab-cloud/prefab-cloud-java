package cloud.prefab.client.internal;

import static cloud.prefab.client.config.TestUtils.getIntConfigValue;
import static cloud.prefab.client.config.TestUtils.getStringConfigValue;
import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LookupContextTest {

  @Test
  void itPrefixesKeysWithContextType() {
    LookupContext lookupContext = new LookupContext(
      Optional.of(getStringConfigValue("coolnamespace")),
      PrefabContext.fromMap(
        "User",
        Map.of(
          "firstname",
          getStringConfigValue("John"),
          "lastname",
          getStringConfigValue("Doe"),
          "age",
          getIntConfigValue(44)
        )
      )
    );

    assertThat(lookupContext.getExpandedProperties())
      .isEqualTo(
        ImmutableMap
          .<String, Prefab.ConfigValue>builder()
          .put(ConfigResolver.NAMESPACE_KEY, getStringConfigValue("coolnamespace"))
          .put(ConfigResolver.NEW_NAMESPACE_KEY, getStringConfigValue("coolnamespace"))
          .put("user.firstname", getStringConfigValue("John"))
          .put("user.lastname", getStringConfigValue("Doe"))
          .put("user.age", getIntConfigValue(44))
          .build()
      );
  }

  @Test
  void itPrefixesKeysWithContextTypeForMultipleContexts() {
    LookupContext lookupContext = new LookupContext(
      Optional.of(getStringConfigValue("coolnamespace")),
      PrefabContextSet.from(
        PrefabContext.fromMap(
          "User",
          Map.of(
            "firstname",
            getStringConfigValue("John"),
            "lastname",
            getStringConfigValue("Doe"),
            "age",
            getIntConfigValue(44)
          )
        ),
        PrefabContext.fromMap("team", Map.of("name", getStringConfigValue("cool team")))
      )
    );

    assertThat(lookupContext.getExpandedProperties())
      .isEqualTo(
        ImmutableMap
          .<String, Prefab.ConfigValue>builder()
          .put(ConfigResolver.NAMESPACE_KEY, getStringConfigValue("coolnamespace"))
          .put(ConfigResolver.NEW_NAMESPACE_KEY, getStringConfigValue("coolnamespace"))
          .put("user.firstname", getStringConfigValue("John"))
          .put("user.lastname", getStringConfigValue("Doe"))
          .put("user.age", getIntConfigValue(44))
          .put("team.name", getStringConfigValue("cool team"))
          .build()
      );
  }

  @Test
  void itRemovesBlankType() {
    LookupContext lookupContext = new LookupContext(
      Optional.of(getStringConfigValue("coolnamespace")),
      PrefabContext.unnamedFromMap(
        Map.of(
          "firstname",
          getStringConfigValue("John"),
          "lastname",
          getStringConfigValue("Doe"),
          "age",
          getIntConfigValue(44)
        )
      )
    );

    assertThat(lookupContext.getExpandedProperties())
      .isEqualTo(
        ImmutableMap
          .<String, Prefab.ConfigValue>builder()
          .put(ConfigResolver.NAMESPACE_KEY, getStringConfigValue("coolnamespace"))
          .put(ConfigResolver.NEW_NAMESPACE_KEY, getStringConfigValue("coolnamespace"))
          .put("firstname", getStringConfigValue("John"))
          .put("lastname", getStringConfigValue("Doe"))
          .put("age", getIntConfigValue(44))
          .build()
      );
  }

  @Test
  void equalsAndHashCodeWorkWithDifferentContextTypeArgs() {
    PrefabContext context = PrefabContext
      .newBuilder("user")
      .put("firstName", "james")
      .build();

    LookupContext lookupContext1 = new LookupContext(
      Optional.of(getStringConfigValue("coolnamespace")),
      context
    );
    LookupContext lookupContext2 = new LookupContext(
      Optional.of(getStringConfigValue("coolnamespace")),
      PrefabContextSet.from(context)
    );

    assertThat(lookupContext1).isEqualTo(lookupContext2);
    assertThat(lookupContext1.hashCode()).isEqualTo(lookupContext2.hashCode());
  }
}
