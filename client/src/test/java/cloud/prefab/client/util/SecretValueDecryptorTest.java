package cloud.prefab.client.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretValueDecryptorTest {

  @Test
  void itDecryptsProperly() {
    String encryptionKey =
      "e657e0406fc22e17d3145966396b2130d33dcb30ac0edd62a77235cdd01fc49d";
    String encryptedValue =
      "b837acfdedb9f6286947fb95f6fb--13490148d8d3ddf0decc3d14--add9b0ed6de775080bec4c5b6025d67e";
    assertThat(SecretValueDecryptor.decryptValueQuietly(encryptionKey, encryptedValue))
      .contains("james-was-here");
  }

  @Test
  void itReturnsEmptyStringWhenKeyIsBad() {
    String encryptionKey =
      "e657e0406fc22e17d3145966396b2130d33dcb30ac0edd62a77235cdd01fc49e";
    String encryptedValue =
      "b837acfdedb9f6286947fb95f6fb--13490148d8d3ddf0decc3d14--add9b0ed6de775080bec4c5b6025d67e";
    assertThat(SecretValueDecryptor.decryptValueQuietly(encryptionKey, encryptedValue))
      .isEmpty();
  }
}
