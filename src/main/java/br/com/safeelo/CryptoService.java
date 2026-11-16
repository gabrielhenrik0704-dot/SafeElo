package br.com.safeelo;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class CryptoService {
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  CryptoService(@Value("${safeelo.encryption-key}") String value) {
    try {
      byte[] bytes = Base64.getDecoder().decode(value);
      if (bytes.length != 32) throw new IllegalArgumentException();
      key = new SecretKeySpec(bytes, "AES");
    } catch (Exception e) {
      throw new IllegalStateException(
          "Configure SAFEELO_ENCRYPTION_KEY com 32 bytes em Base64. Use scripts/run-local.sh para"
              + " gerar uma chave local.");
    }
  }

  String encrypt(String value, String purpose) {
    if (value == null || value.isBlank()) return null;
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      c.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
      return "v1:"
          + Base64.getEncoder().encodeToString(iv)
          + ":"
          + Base64.getEncoder().encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao proteger o registro.");
    }
  }

  String decrypt(String value, String purpose) {
    if (value == null) return "";
    try {
      String[] p = value.split(":");
      if (p.length != 3 || !p[0].equals("v1")) throw new IllegalArgumentException();
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getDecoder().decode(p[1])));
      c.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
      return new String(c.doFinal(Base64.getDecoder().decode(p[2])), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Não foi possível abrir o registro. Verifique a chave de criptografia do servidor.");
    }
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  String token() {
    byte[] b = new byte[32];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }
}
