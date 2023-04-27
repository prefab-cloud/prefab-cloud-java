package cloud.prefab.context;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.domain.Prefab;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrefabContextSetTest {

  static final PrefabContext PREFAB_USER_CONTEXT_1 = PrefabContext.fromMap(
    "User",
    Map.of(
      "firstName",
      Prefab.ConfigValue.newBuilder().setString("James").build(),
      "isHuman",
      Prefab.ConfigValue.newBuilder().setBool(true).build()
    )
  );

  static final PrefabContext PREFAB_USER_CONTEXT_1_LOWERCASE = PrefabContext.fromMap(
    "user",
    Map.of(
      "firstName",
      Prefab.ConfigValue.newBuilder().setString("James").build(),
      "isHuman",
      Prefab.ConfigValue.newBuilder().setBool(true).build()
    )
  );

  static final PrefabContext PREFAB_USER_CONTEXT_2 = PrefabContext.fromMap(
    "User",
    Map.of(
      "firstName",
      Prefab.ConfigValue.newBuilder().setString("Johnny").build(),
      "isHuman",
      Prefab.ConfigValue.newBuilder().setBool(false).build()
    )
  );

  static final PrefabContext PREFAB_COMPANY_CONTEXT = PrefabContext.fromMap(
    "Company",
    Map.of("Name", Prefab.ConfigValue.newBuilder().setString("Enron").build())
  );

  @Test
  void itIgnoresNullContextWhenAdding() {
    PrefabContextSet prefabContextSet = new PrefabContextSet();
    prefabContextSet.addContext(null);
    assertThat(prefabContextSet.isEmpty()).isTrue();
  }

  @Test
  void lastContextAddedForEachTypeWins() {
    PrefabContextSet prefabContextSet = new PrefabContextSet();
    prefabContextSet.addContext(PREFAB_USER_CONTEXT_1);
    prefabContextSet.addContext(PREFAB_COMPANY_CONTEXT);
    prefabContextSet.addContext(PREFAB_USER_CONTEXT_2);

    assertThat(prefabContextSet.getContexts()).hasSize(2);
    assertThat(prefabContextSet.getByName("Company")).contains(PREFAB_COMPANY_CONTEXT);
    assertThat(prefabContextSet.getByName("User")).contains(PREFAB_USER_CONTEXT_2);
  }

  @Test
  void lastContextAddedForEachTypeWinsInFromMethod() {
    PrefabContextSet prefabContextSet = PrefabContextSet.from(
      PREFAB_USER_CONTEXT_1,
      PREFAB_COMPANY_CONTEXT,
      PREFAB_USER_CONTEXT_2
    );

    assertThat(prefabContextSet.getContexts()).hasSize(2);
    assertThat(prefabContextSet.getByName("Company")).contains(PREFAB_COMPANY_CONTEXT);
    assertThat(prefabContextSet.getByName("User")).contains(PREFAB_USER_CONTEXT_2);
  }

  @Test
  void itIsCaseInsensitive() {
    PrefabContextSet prefabContextSet = PrefabContextSet.from(
      PREFAB_USER_CONTEXT_2,
      PREFAB_COMPANY_CONTEXT,
      PREFAB_USER_CONTEXT_1_LOWERCASE
    );

    assertThat(prefabContextSet.getContexts()).hasSize(2);
    assertThat(prefabContextSet.getByName("Company")).contains(PREFAB_COMPANY_CONTEXT);
    assertThat(prefabContextSet.getByName("User"))
      .contains(PREFAB_USER_CONTEXT_1_LOWERCASE);
    assertThat(prefabContextSet.getByName("user"))
      .contains(PREFAB_USER_CONTEXT_1_LOWERCASE);
  }

  @Test
  void convertWorksForSetReadableCase() {
    assertThat(PrefabContextSet.convert(PREFAB_COMPANY_CONTEXT))
      .isEqualTo(PrefabContextSet.from(PREFAB_COMPANY_CONTEXT));
  }

  @Test
  void convertWorksForContextSetCase() {
    PrefabContextSet prefabContextSet = PrefabContextSet.from(PREFAB_COMPANY_CONTEXT);
    assertThat(PrefabContextSet.convert(prefabContextSet)).isEqualTo(prefabContextSet);
  }
}
