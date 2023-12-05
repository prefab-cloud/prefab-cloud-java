package cloud.prefab.client.util;

import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.List;
import java.util.Optional;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretValueDecryptor {

  private static final Logger LOG = LoggerFactory.getLogger(SecretValueDecryptor.class);

  private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

  public static Optional<String> decryptValueQuietly(
    String secretKeyString,
    String value
  ) {
    try {
      return Optional.of(decryptValue(secretKeyString, value));
    } catch (
      InvalidAlgorithmParameterException
      | IllegalBlockSizeException
      | BadPaddingException
      | InvalidKeyException e
    ) {
      LOG.error("unable to decrypt value {} due to exception", value, e);
      return Optional.empty();
    }
  }

  public static String decryptValue(String secretKeyString, String value)
    throws InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
    SecretKey secretKey = new SecretKeySpec(
      BaseEncoding.base16().decode(secretKeyString.toUpperCase()),
      "AES"
    );
    List<String> parts = Splitter.on("--").limit(3).splitToList(value.toUpperCase());
    String dataStr = parts.get(0);
    String ivStr = parts.get(1);
    String authTagStr = parts.get(2);
    byte[] iv = BaseEncoding.base16().decode(ivStr);
    byte[] dataToProcess = BaseEncoding.base16().decode(dataStr + authTagStr);
    AlgorithmParameterSpec gcmIv = new GCMParameterSpec(128, iv);

    final Cipher cipher = getCipher();
    cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);
    cipher.updateAAD("".getBytes());
    byte[] decryptedData = cipher.doFinal(dataToProcess);
    return new String(decryptedData);
  }

  private static Cipher getCipher() {
    try {
      return Cipher.getInstance(CIPHER_TRANSFORMATION);
    } catch (NoSuchPaddingException e) {
      LOG.error(
        "NoSuchPaddingException encountered getting instance for {}",
        CIPHER_TRANSFORMATION,
        e
      );
      throw new RuntimeException(e);
    } catch (NoSuchAlgorithmException e) {
      LOG.error(
        "NoSuchAlgorithmException encountered getting instance for {}",
        CIPHER_TRANSFORMATION,
        e
      );
      throw new RuntimeException(e);
    }
  }
}
