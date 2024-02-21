package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cloud.prefab.client.util.RandomProviderIF;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import com.google.common.hash.HashCode;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeightedValueEvaluatorTest {

  @Mock
  RandomProviderIF randomProvider;

  @Mock
  HashProvider hashProvider;

  @Mock
  HashCode hashCode;

  @InjectMocks
  WeightedValueEvaluator weightedValueEvaluator;

  @Test
  void itUsesRandomWhenHashPropertyNameIsEmpty() {
    when(randomProvider.random()).thenReturn(0.99);
    Prefab.WeightedValues weightedValues = getTrueFalseConfig(500, 500, Optional.empty());

    Prefab.ConfigValue foo = weightedValueEvaluator.toValue(
      weightedValues,
      "featureName",
      new LookupContext(PrefabContextSetReadable.EMPTY)
    );
    assertThat(foo).isEqualTo(Prefab.ConfigValue.newBuilder().setBool(false).build());
  }

  @ParameterizedTest
  @MethodSource("provideArgumentsForRandomTrueFalse")
  void itUsesRandomWhenHashPropertyNameIsPresentButNoDataInContext(
    double random,
    boolean expectedValue
  ) {
    when(randomProvider.random()).thenReturn(random);
    Prefab.WeightedValues weightedValues = getTrueFalseConfig(
      500,
      500,
      Optional.of("user.name")
    );

    Prefab.ConfigValue foo = weightedValueEvaluator.toValue(
      weightedValues,
      "featureName",
      new LookupContext(PrefabContextSetReadable.EMPTY)
    );
    assertThat(foo)
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(expectedValue).build());
  }

  @ParameterizedTest
  @MethodSource("provideArgumentsForHashingTrueFalse")
  void itUsesHashFunctionWhenAble(double hashValue, boolean expectedValue) {
    when(hashProvider.hash("featureNamejames")).thenReturn(hashValue);
    Prefab.WeightedValues weightedValues = getTrueFalseConfig(
      500,
      500,
      Optional.of("user.name")
    );

    Prefab.ConfigValue value = weightedValueEvaluator.toValue(
      weightedValues,
      "featureName",
      new LookupContext(PrefabContext.newBuilder("user").put("name", "james").build())
    );
    assertThat(value)
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(expectedValue).build());
  }

  @ParameterizedTest
  @MethodSource("provideArgumentsForHashingFourWaySplit")
  void itUsesHashFunctionWhenAbleFourWaySplit(double hashValue, int expectedValue) {
    when(hashProvider.hash("featureNamejames")).thenReturn(hashValue);
    Prefab.WeightedValues weightedValues = getMultiPartConfig(
      Optional.of("user.name"),
      10,
      10,
      10,
      10
    );

    Prefab.ConfigValue value = weightedValueEvaluator.toValue(
      weightedValues,
      "featureName",
      new LookupContext(PrefabContext.newBuilder("user").put("name", "james").build())
    );
    assertThat(value)
      .isEqualTo(Prefab.ConfigValue.newBuilder().setInt(expectedValue).build());
  }

  private static Prefab.WeightedValues getTrueFalseConfig(
    int trueWeight,
    int falseWeight,
    Optional<String> hashByPropertyName
  ) {
    Prefab.WeightedValues.Builder wvBuilder = Prefab.WeightedValues
      .newBuilder()
      .addWeightedValues(
        Prefab.WeightedValue
          .newBuilder()
          .setWeight(trueWeight)
          .setValue(Prefab.ConfigValue.newBuilder().setBool(true).build())
          .build()
      )
      .addWeightedValues(
        Prefab.WeightedValue
          .newBuilder()
          .setWeight(falseWeight)
          .setValue(Prefab.ConfigValue.newBuilder().setBool(false).build())
          .build()
      );

    hashByPropertyName.ifPresent(wvBuilder::setHashByPropertyName);

    return wvBuilder.build();
  }

  private static Prefab.WeightedValues getMultiPartConfig(
    Optional<String> hashByPropertyName,
    int... weights
  ) {
    Prefab.WeightedValues.Builder wvBuilder = Prefab.WeightedValues.newBuilder();
    hashByPropertyName.ifPresent(wvBuilder::setHashByPropertyName);

    int weightIndex = 1;
    for (int weight : weights) {
      wvBuilder.addWeightedValues(
        Prefab.WeightedValue
          .newBuilder()
          .setWeight(weight)
          .setValue(Prefab.ConfigValue.newBuilder().setInt(weightIndex).build())
          .build()
      );

      weightIndex++;
    }
    return wvBuilder.build();
  }

  private static Stream<Arguments> provideArgumentsForHashingTrueFalse() {
    return Stream.of(
      Arguments.of(0, true),
      Arguments.of(0.23, true),
      Arguments.of(0.8, false),
      Arguments.of(1, false)
    );
  }

  private static Stream<Arguments> provideArgumentsForHashingFourWaySplit() {
    return Stream.of(
      Arguments.of(0, 1),
      Arguments.of(0.3, 2),
      Arguments.of(0.50001, 3),
      Arguments.of(1, 4)
    );
  }

  private static Stream<Arguments> provideArgumentsForRandomTrueFalse() {
    return Stream.of(
      Arguments.of(0d, true),
      Arguments.of(0.25d, true),
      Arguments.of(0.49d, true),
      Arguments.of(0.5d, true),
      Arguments.of(0.51d, false),
      Arguments.of(0.75, false),
      Arguments.of(1d, false)
    );
  }
}
