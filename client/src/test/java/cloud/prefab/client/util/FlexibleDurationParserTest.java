package cloud.prefab.client.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FlexibleDurationParserTest {

  @ParameterizedTest
  @MethodSource("provideDurationAndMillisPairs")
  void testWithStringAndNumberPairs(String durationText, long millis) {
    assertThat(FlexibleDurationParser.parse(durationText).toMillis()).isEqualTo(millis);
  }

  private static Stream<Arguments> provideDurationAndMillisPairs() {
    return Stream.of(
      Arguments.of("PT0M0S", 0),
      Arguments.of("PT90S", TimeUnit.SECONDS.toMillis(90)),
      Arguments.of("PT5M", TimeUnit.MINUTES.toMillis(5)),
      Arguments.of("P1D", TimeUnit.DAYS.toMillis(1)),
      Arguments.of("P1DT5M", TimeUnit.DAYS.toMillis(1) + TimeUnit.MINUTES.toMillis(5)),
      Arguments.of("P0.75D", (long) (TimeUnit.DAYS.toMillis(1) * 0.75)),
      Arguments.of("PT0.5S", (long) (TimeUnit.SECONDS.toMillis(1) * 0.5)),
      Arguments.of(
        "P2DT1H3M.5S",
        (long) (
          TimeUnit.DAYS.toMillis(2) +
          TimeUnit.HOURS.toMillis(1) +
          TimeUnit.MINUTES.toMillis(3) +
          TimeUnit.SECONDS.toMillis(1) *
          0.5
        )
      )
    );
  }
}
