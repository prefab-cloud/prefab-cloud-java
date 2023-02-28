package cloud.prefab.client.value;

import com.google.common.base.Optional;
import java.util.function.Function;

public class FixedValue<T> implements Value<T> {

  private final Optional<T> value;

  public FixedValue() {
    this.value = Optional.absent();
  }

  public FixedValue(T value) {
    this.value = Optional.of(value);
  }

  @Override
  public T get() {
    if (value.isPresent()) {
      return value.get();
    } else {
      throw new RuntimeException("Fixed Value Unset");
    }
  }

  @Override
  public T or(T defaultValue) {
    return value.or(defaultValue);
  }

  @Override
  public T orNull() {
    if (value.isPresent()) {
      return value.get();
    } else {
      return null;
    }
  }

  public static <T> FixedValue<T> of(T value) {
    return new FixedValue<T>(value);
  }

  public static <T> FixedValue<T> absent() {
    return new FixedValue<T>();
  }

  @Override
  public String toString() {
    return String.format("FixedValue{}=%s", value.orNull());
  }
}
