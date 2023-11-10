package cloud.prefab.client.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.PrefabInitializationTimeoutException;
import cloud.prefab.client.integration.IntegrationTestExpectation.VerifyException;
import cloud.prefab.client.integration.IntegrationTestExpectation.VerifyPost;
import cloud.prefab.client.integration.IntegrationTestExpectation.VerifyReturnValue;
import cloud.prefab.client.value.UndefinedKeyException;
import cloud.prefab.domain.Prefab;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonTypeInfo(use = Id.NAME, property = "status", defaultImpl = VerifyReturnValue.class)
@JsonSubTypes(
  {
    @Type(value = VerifyReturnValue.class, name = "return"),
    @Type(value = VerifyException.class, name = "raise"),
  }
)
public interface IntegrationTestExpectation {
  Logger LOG = LoggerFactory.getLogger(IntegrationTestExpectation.class);

  void verifyScenario(
    PrefabCloudClient client,
    IntegrationTestFunction function,
    IntegrationTestInput input
  );

  class VerifyReturnValue implements IntegrationTestExpectation {

    @Nullable
    private final Object expectedValue;

    @JsonCreator
    private VerifyReturnValue(@JsonProperty("value") @Nullable Object expectedValue) {
      this.expectedValue = expectedValue;
    }

    @Override
    public void verifyScenario(
      PrefabCloudClient client,
      IntegrationTestFunction function,
      IntegrationTestInput input
    ) {
      Object actualValue = function.apply(client, input);
      if (expectedValue == null) {
        assertThat(actualValue).isNull();
      } else if (expectedValue instanceof Number && actualValue instanceof Number) {
        assertThat(String.valueOf(actualValue)).isEqualTo(String.valueOf(expectedValue));
      } else if (actualValue instanceof Prefab.LogLevel) {
        assertThat(((Prefab.LogLevel) actualValue).name()).isEqualTo(expectedValue);
      } else {
        assertThat(actualValue).isEqualTo(expectedValue);
      }
    }
  }

  class VerifyException implements IntegrationTestExpectation {

    private static final Map<String, Class<?>> ERROR_TYPES = ImmutableMap.of(
      "missing_default",
      UndefinedKeyException.class,
      "initialization_timeout",
      PrefabInitializationTimeoutException.class
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

  class VerifyPost implements IntegrationTestExpectation {

    @Override
    public void verifyScenario(
      PrefabCloudClient client,
      IntegrationTestFunction function,
      IntegrationTestInput input
    ) {
      function.apply(client, input);

      assertThat(client.getOptions().getTelemetryListener()).isPresent();
      assertThat(client.getOptions().getTelemetryListener().get())
        .isInstanceOf(TelemetryAccumulator.class);

      TelemetryAccumulator telemetryAccumulator = (TelemetryAccumulator) client
        .getOptions()
        .getTelemetryListener()
        .orElseThrow();

      await()
        .atMost(Duration.of(30, ChronoUnit.SECONDS))
        .untilAsserted(() -> {
          assertThat(telemetryAccumulator.getTelemetryEventsList()).hasSize(10);
        });
    }
  }
}
