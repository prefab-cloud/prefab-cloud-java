package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.context.PrefabContextSetReadable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TypedConfigClientImplTest {

  @Mock
  ConfigClientCore configClientCore;

  @InjectMocks
  TypedConfigClientImpl impl;

  @BeforeEach
  void setUp() {}

  @Nested
  class BooleanTests {

    @Test
    void getReturnsResult() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from(false)));
      assertThat(impl.getBoolean("key", true, null)).isFalse();
    }

    @Test
    void getReturnsDefaultValueWhenNoConfigExists() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.empty());
      assertThat(impl.getBoolean("key", true, null)).isTrue();
    }

    @Test
    void getReturnsDefaultValueWhenConfigIsWrongType() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from(123)));
      assertThat(impl.getBoolean("key", true, null)).isTrue();
    }

    @Test
    void getReturnsDefaultValueWhenExceptionIsThrown() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenThrow(new RuntimeException("something"));
      assertThat(impl.getBoolean("key", true, null)).isTrue();
    }
  }

  @Nested
  class LongTests {

    @Test
    void getReturnsResult() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from(10)));
      assertThat(impl.getLong("key", 1234, null)).isEqualTo(10);
    }

    @Test
    void getReturnsDefaultValueWhenNoConfigExists() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.empty());
      assertThat(impl.getLong("key", 1234, null)).isEqualTo(1234);
    }

    @Test
    void getReturnsDefaultValueWhenConfigIsWrongType() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from("hello")));
      assertThat(impl.getLong("key", 1234, null)).isEqualTo(1234);
    }

    @Test
    void getReturnsDefaultValueWhenExceptionIsThrown() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenThrow(new RuntimeException("something"));
      assertThat(impl.getLong("key", 1234, null)).isEqualTo(1234);
    }
  }

  @Nested
  class DoubleTests {

    @Test
    void getReturnsResult() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from(10.1)));
      assertThat(impl.getDouble("key", 12.34, null)).isEqualTo(10.1);
    }

    @Test
    void getReturnsDefaultValueWhenNoConfigExists() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.empty());
      assertThat(impl.getDouble("key", 12.34, null)).isEqualTo(12.34);
    }

    @Test
    void getReturnsDefaultValueWhenConfigIsWrongType() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from("hello")));
      assertThat(impl.getDouble("key", 12.34, null)).isEqualTo(12.34);
    }

    @Test
    void getReturnsDefaultValueWhenExceptionIsThrown() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenThrow(new RuntimeException("something"));
      assertThat(impl.getDouble("key", 12.34, null)).isEqualTo(12.34);
    }
  }

  @Nested
  class DurationTest {

    @Test
    void getReturnsResult() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from(Duration.ofSeconds(90))));
      assertThat(impl.getDuration("key", Duration.ofMinutes(10), null))
        .isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void getReturnsDefaultValueWhenNoConfigExists() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.empty());
      assertThat(impl.getDuration("key", Duration.ofMinutes(10), null))
        .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void getReturnsDefaultValueWhenConfigIsWrongType() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from("hello")));
      assertThat(impl.getDuration("key", Duration.ofMinutes(10), null))
        .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void getReturnsDefaultValueWhenExceptionIsThrown() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenThrow(new RuntimeException("something"));
      assertThat(impl.getDuration("key", Duration.ofMinutes(10), null))
        .isEqualTo(Duration.ofMinutes(10));
    }
  }

  @Nested
  class StringTest {

    @Test
    void getReturnsResult() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from("hello")));
      assertThat(impl.getString("key", "world", null)).isEqualTo("hello");
    }

    @Test
    void getReturnsDefaultValueWhenNoConfigExists() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.empty());
      assertThat(impl.getString("key", "hello", null)).isEqualTo("hello");
    }

    @Test
    void getReturnsDefaultValueWhenConfigIsWrongType() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from(1234)));
      assertThat(impl.getString("key", "hello", null)).isEqualTo("hello");
    }

    @Test
    void getReturnsDefaultValueWhenExceptionIsThrown() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenThrow(new RuntimeException("something"));
      assertThat(impl.getString("key", "hello", null)).isEqualTo("hello");
    }
  }

  @Nested
  class StringListTest {

    @Test
    void getReturnsResult() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from(List.of("a", "b"))));
      assertThat(impl.getStringList("key", List.of("a"), null))
        .isEqualTo(List.of("a", "b"));
    }

    @Test
    void getReturnsDefaultValueWhenNoConfigExists() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.empty());
      assertThat(impl.getStringList("key", List.of("a", "b"), null))
        .isEqualTo(List.of("a", "b"));
    }

    @Test
    void getReturnsDefaultValueWhenConfigIsWrongType() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenReturn(Optional.of(ConfigValueUtils.from("hello")));
      assertThat(impl.getStringList("key", List.of("a", "b"), null))
        .isEqualTo(List.of("a", "b"));
    }

    @Test
    void getReturnsDefaultValueWhenExceptionIsThrown() {
      when(configClientCore.get("key", (PrefabContextSetReadable) null))
        .thenThrow(new RuntimeException("something"));
      assertThat(impl.getStringList("key", List.of("a", "b"), null))
        .isEqualTo(List.of("a", "b"));
    }
  }
}
