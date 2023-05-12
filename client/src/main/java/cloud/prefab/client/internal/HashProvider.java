package cloud.prefab.client.internal;

import com.google.common.hash.Hashing;
import com.google.common.primitives.UnsignedInts;
import java.nio.charset.StandardCharsets;

public interface HashProvider {
  long UNSIGNED_INT_MAX = Integer.MAX_VALUE + (long) Integer.MAX_VALUE;

  /**
   * Maps a string argument onto the space 0..1 using Hashing.murmur3_32_fixed()
   * @param string
   * @return
   */
  default double hash(String string) {
    long value = UnsignedInts.toLong(
      Hashing.murmur3_32_fixed().hashString(string, StandardCharsets.UTF_8).asInt()
    );
    return value / (double) UNSIGNED_INT_MAX;
  }

  HashProvider DEFAULT = new HashProvider() {};
}
