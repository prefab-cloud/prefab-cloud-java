package cloud.prefab.client.internal;

import cloud.prefab.domain.Prefab;
import java.util.Optional;

public class IntRangeWrapper {

  private final Prefab.IntRange intRange;

  public static IntRangeWrapper of(Prefab.IntRange intRange) {
    return new IntRangeWrapper(intRange);
  }

  private IntRangeWrapper(Prefab.IntRange intRange) {
    this.intRange = intRange;
  }

  public Optional<Long> getStart() {
    if (intRange.hasStart()) {
      return Optional.of(intRange.getStart());
    }
    return Optional.empty();
  }

  public Optional<Long> getEnd() {
    if (intRange.hasEnd()) {
      return Optional.of(intRange.getEnd());
    }
    return Optional.empty();
  }

  public boolean contains(long value) {
    return (
      value >= getStart().orElse(Long.MIN_VALUE) &&
      value < getEnd().orElse(Long.MAX_VALUE)
    );
  }
}
