package cloud.prefab.client.integration;

import cloud.prefab.client.PrefabCloudClient;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

public enum IntegrationTestFunction {
  GET_OR_RAISE("get_or_raise") {
    @Override
    public String apply(PrefabCloudClient client, IntegrationTestInput input) {
      return input.getWithoutFallback(client);
    }
  },
  GET("get") {
    @Override
    public String apply(PrefabCloudClient client, IntegrationTestInput input) {
      if (input.getFlag().isPresent()) {
        return String.valueOf(input.getFeatureFor(client));
      } else {
        return input.getWithFallback(client);
      }
    }
  },
  ENABLED("enabled") {
    @Override
    public String apply(PrefabCloudClient client, IntegrationTestInput input) {
      return String.valueOf(input.featureIsOnFor(client));
    }
  };

  private static final Map<String, IntegrationTestFunction> JSON_INDEX = Arrays
    .stream(IntegrationTestFunction.values())
    .collect(
      ImmutableMap.toImmutableMap(
        IntegrationTestFunction::getJsonValue,
        Function.identity()
      )
    );

  private final String jsonValue;

  IntegrationTestFunction(String jsonValue) {
    this.jsonValue = jsonValue;
  }

  @JsonCreator
  public static IntegrationTestFunction fromJsonValue(String jsonValue) {
    return JSON_INDEX.get(jsonValue);
  }

  @JsonValue
  public String getJsonValue() {
    return jsonValue;
  }

  public abstract String apply(PrefabCloudClient client, IntegrationTestInput input);
}
