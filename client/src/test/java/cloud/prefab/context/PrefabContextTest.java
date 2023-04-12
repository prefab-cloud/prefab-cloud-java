package cloud.prefab.context;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.domain.Prefab;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrefabContextTest {

  @Test
  void itBuildsWithExpectedProperties() {
    String type = "User";
    String key = "user1234";
    String firstname = "Joe";
    String lastname = "Smith";
    long age = 56;
    double pi = 3.14;
    boolean customer = true;

    PrefabContext prefabContext = PrefabContext
      .newBuilder(type)
      .withKey(key)
      .set("firstname", firstname)
      .set("lastname", lastname)
      .set("age", age)
      .set("pi", pi)
      .set("isCustomer", customer)
      .build();

    assertThat(prefabContext.getContextType()).isEqualTo(type);
    assertThat(prefabContext.getKey()).isEqualTo(key);
    assertThat(prefabContext.getProperties())
      .isEqualTo(
        Map.of(
          "firstname",
          Prefab.ConfigValue.newBuilder().setString(firstname).build(),
          "lastname",
          Prefab.ConfigValue.newBuilder().setString(lastname).build(),
          "age",
          Prefab.ConfigValue.newBuilder().setInt(age).build(),
          "pi",
          Prefab.ConfigValue.newBuilder().setDouble(pi).build(),
          "isCustomer",
          Prefab.ConfigValue.newBuilder().setBool(customer).build()
        )
      );
  }
}
