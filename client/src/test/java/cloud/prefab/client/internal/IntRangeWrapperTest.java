package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.domain.Prefab;
import org.junit.jupiter.api.Test;

class IntRangeWrapperTest {

  @Test
  void itContainsWithNoBoundsSet() {
    assertThat(IntRangeWrapper.of(Prefab.IntRange.newBuilder().build()).contains(0))
      .isTrue();
  }

  @Test
  void itContainsWithBoundsSet() {
    IntRangeWrapper wrapper = IntRangeWrapper.of(
      Prefab.IntRange.newBuilder().setStart(200).setEnd(300).build()
    );
    assertThat(wrapper.contains(200)).as("contains 200").isTrue();
    assertThat(wrapper.contains(299)).as("contains 299").isTrue();
  }

  @Test
  void itRejectsBelowBounds() {
    IntRangeWrapper wrapper = IntRangeWrapper.of(
      Prefab.IntRange.newBuilder().setStart(200).setEnd(300).build()
    );
    assertThat(wrapper.contains(199)).as("does not contain 199").isFalse();
    assertThat(wrapper.contains(-100)).as("does not contain -100").isFalse();
  }

  @Test
  void itRejectsAtOrAboveBounds() {
    IntRangeWrapper wrapper = IntRangeWrapper.of(
      Prefab.IntRange.newBuilder().setStart(200).setEnd(300).build()
    );
    assertThat(wrapper.contains(300)).as("does not contain 300").isFalse();
    assertThat(wrapper.contains(301)).as("does not contain 301").isFalse();
  }
}
