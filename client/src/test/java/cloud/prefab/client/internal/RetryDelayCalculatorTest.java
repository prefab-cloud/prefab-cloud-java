package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RetryDelayCalculatorTest {

  @ParameterizedTest
  @MethodSource("provideExpectedValues")
  void itReturnsCorrectValues(int errorCount, long expectedDelayValue) {
    assertThat(RetryDelayCalculator.exponentialMillisToNextTry(errorCount, 1000, 10000))
      .isEqualTo(expectedDelayValue);
  }

  private static Stream<Arguments> provideExpectedValues() {
    return Stream.of(
      Arguments.of(0, 0),
      Arguments.of(1, 1000),
      Arguments.of(2, 2000),
      Arguments.of(3, 4000),
      Arguments.of(4, 8000),
      Arguments.of(5, 10000),
      Arguments.of(10, 10000)
    );
  }
}
