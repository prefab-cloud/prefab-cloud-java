package cloud.prefab.context;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.domain.Prefab;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextTest {

  @Test
  void itBuildsWithExpectedProperties() {
    String type = "User";
    String key = "user1234";
    String firstname = "Joe";
    String lastname = "Smith";
    long age = 56;
    double pi = 3.14;
    boolean customer = true;

    Context context = Context
      .newBuilder(type)
      .withKey(key)
      .set("firstname", firstname)
      .set("lastname", lastname)
      .set("age", age)
      .set("pi", pi)
      .set("isCustomer", customer)
      .build();

    assertThat(context.getContextType()).isEqualTo(type);
    assertThat(context.getKey()).isEqualTo(key);
    assertThat(context.getProperties())
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
