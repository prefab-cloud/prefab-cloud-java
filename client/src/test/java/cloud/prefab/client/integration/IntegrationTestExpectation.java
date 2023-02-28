package cloud.prefab.client.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.integration.IntegrationTestExpectation.VerifyException;
import cloud.prefab.client.integration.IntegrationTestExpectation.VerifyReturnValue;
import cloud.prefab.client.value.UndefinedKeyException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;

@JsonTypeInfo(use = Id.NAME, property = "status", defaultImpl = VerifyReturnValue.class)
@JsonSubTypes(
  {
    @Type(value = VerifyReturnValue.class, name = "return"),
    @Type(value = VerifyException.class, name = "raise"),
  }
)
public interface IntegrationTestExpectation {
  void verifyScenario(
    PrefabCloudClient client,
    IntegrationTestFunction function,
    IntegrationTestInput input
  );

  class VerifyReturnValue implements IntegrationTestExpectation {

    @Nullable
    private final String expectedValue;

    @JsonCreator
    private VerifyReturnValue(@JsonProperty("value") @Nullable String expectedValue) {
      this.expectedValue = expectedValue;
    }

    @Override
    public void verifyScenario(
      PrefabCloudClient client,
      IntegrationTestFunction function,
      IntegrationTestInput input
    ) {
      assertThat(function.apply(client, input)).isEqualTo(expectedValue);
    }
  }

  class VerifyException implements IntegrationTestExpectation {

    private static final Map<String, Class<?>> ERROR_TYPES = ImmutableMap.of(
      "missing_default",
      UndefinedKeyException.class
    );

    private final String error;
    private final String message;

    @JsonCreator
    public VerifyException(
      @JsonProperty("error") String error,
      @JsonProperty("message") String message
    ) {
      this.error = error;
      this.message = message;
    }

    @Override
    public void verifyScenario(
      PrefabCloudClient client,
      IntegrationTestFunction function,
      IntegrationTestInput input
    ) {
      Class<?> errorClass = ERROR_TYPES.get(error);
      assertThat(errorClass).describedAs("Unknown error type: %s", error).isNotNull();

      Throwable t = catchThrowable(() -> function.apply(client, input));
      // TODO: the error messages are not currently standard across our clients.
      //assertThat(t).isInstanceOf(errorClass).hasMessageContaining(message);
      assertThat(t).isInstanceOf(errorClass);
    }
  }
}
