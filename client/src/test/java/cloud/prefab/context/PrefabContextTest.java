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
      .put("firstname", firstname)
      .put("lastname", lastname)
      .put("age", age)
      .put("pi", pi)
      .put("isCustomer", customer)
      .build();

    assertThat(prefabContext.getName()).isEqualTo(type);
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

    assertThat(prefabContext.getNameQualifiedProperties())
      .isEqualTo(
        Map.of(
          "user.firstname",
          Prefab.ConfigValue.newBuilder().setString(firstname).build(),
          "user.lastname",
          Prefab.ConfigValue.newBuilder().setString(lastname).build(),
          "user.age",
          Prefab.ConfigValue.newBuilder().setInt(age).build(),
          "user.pi",
          Prefab.ConfigValue.newBuilder().setDouble(pi).build(),
          "user.isCustomer",
          Prefab.ConfigValue.newBuilder().setBool(customer).build()
        )
      );
  }

  @Test
  void itBuildsNestedWhenNameIsNotSet() {
    String key = "user1234";
    String firstname = "Joe";
    String lastname = "Smith";
    long age = 56;
    double pi = 3.14;
    boolean customer = true;

    PrefabContext prefabContext = PrefabContext
      .newBuilder("")
      .put("firstname", firstname)
      .put("lastname", lastname)
      .put("age", age)
      .put("pi", pi)
      .put("isCustomer", customer)
      .build();

    assertThat(prefabContext.getNameQualifiedProperties())
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
