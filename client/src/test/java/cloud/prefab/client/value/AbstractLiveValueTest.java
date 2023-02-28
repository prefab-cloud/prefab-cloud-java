package cloud.prefab.client.value;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractLiveValueTest {

  private ConfigClient mockConfigClient;

  @BeforeEach
  void setup() {
    mockConfigClient = mock(ConfigClient.class);
    when(mockConfigClient.get("long"))
      .thenReturn(Optional.of(Prefab.ConfigValue.newBuilder().setInt(1L).build()));
    when(mockConfigClient.get("long mismatch"))
      .thenReturn(
        Optional.of(Prefab.ConfigValue.newBuilder().setString("mismatch").build())
      );
    when(mockConfigClient.get("string"))
      .thenReturn(
        Optional.of(Prefab.ConfigValue.newBuilder().setString("string val").build())
      );
    when(mockConfigClient.get("string mismatch"))
      .thenReturn(Optional.of(Prefab.ConfigValue.newBuilder().setInt(0L).build()));
    when(mockConfigClient.get("double"))
      .thenReturn(Optional.of(Prefab.ConfigValue.newBuilder().setDouble(2.3).build()));
    when(mockConfigClient.get("double mismatch"))
      .thenReturn(
        Optional.of(Prefab.ConfigValue.newBuilder().setString("mismatch").build())
      );
    when(mockConfigClient.get("boolean"))
      .thenReturn(Optional.of(Prefab.ConfigValue.newBuilder().setBool(false).build()));
    when(mockConfigClient.get("boolean mismatch"))
      .thenReturn(
        Optional.of(Prefab.ConfigValue.newBuilder().setString("mismatch").build())
      );
  }

  @Test
  void get() {
    assertEquals(1L, new LiveLong(mockConfigClient, "long").get());
    assertThrows(
      TypeMismatchException.class,
      () -> new LiveLong(mockConfigClient, "long mismatch").get()
    );
    assertEquals("string val", new LiveString(mockConfigClient, "string").get());
    assertThrows(
      TypeMismatchException.class,
      () -> new LiveString(mockConfigClient, "string mismatch").get()
    );
    assertEquals(2.3, new LiveDouble(mockConfigClient, "double").get());
    assertThrows(
      TypeMismatchException.class,
      () -> new LiveDouble(mockConfigClient, "double mismatch").get()
    );
    assertEquals(false, new LiveBoolean(mockConfigClient, "boolean").get());
    assertThrows(
      TypeMismatchException.class,
      () -> new LiveBoolean(mockConfigClient, "boolean mismatch").get()
    );
  }

  @Test
  void or() {
    assertEquals(2L, new LiveLong(mockConfigClient, "missing").orElse(2L));
    assertEquals("or", new LiveString(mockConfigClient, "missing").orElse("or"));
    assertEquals(4.5, new LiveDouble(mockConfigClient, "missing").orElse(4.5));
    assertEquals(false, new LiveBoolean(mockConfigClient, "missing").orElse(false));

    assertEquals(2L, new LiveLong(mockConfigClient, "long mismatch").orElse(2L));
    assertEquals("or", new LiveString(mockConfigClient, "string mismatch").orElse("or"));
    assertEquals(4.5, new LiveDouble(mockConfigClient, "double mismatch").orElse(4.5));
    assertEquals(
      false,
      new LiveBoolean(mockConfigClient, "boolean mismatch").orElse(false)
    );
  }

  @Test
  void orNull() {
    assertEquals(null, new LiveLong(mockConfigClient, "missing").orNull());
    assertEquals(null, new LiveString(mockConfigClient, "missing").orNull());
    assertEquals(null, new LiveDouble(mockConfigClient, "missing").orNull());
    assertEquals(null, new LiveBoolean(mockConfigClient, "missing").orNull());
  }
}
