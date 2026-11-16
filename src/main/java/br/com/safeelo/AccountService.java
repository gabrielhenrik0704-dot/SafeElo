package br.com.safeelo;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AccountService {
  final UserRepository users;
  final PasswordEncoder passwords;
  final CryptoService crypto;

  AccountService(UserRepository u, PasswordEncoder p, CryptoService c) {
    users = u;
    passwords = p;
    crypto = c;
  }

  UserRepository users() {
    return users;
  }

  CryptoService crypto() {
    return crypto;
  }

  PasswordEncoder passwords() {
    return passwords;
  }

  AppUser current(Authentication auth) {
    return users
        .findByEmail(auth.getName())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre novamente."));
  }

  void citizen(AppUser u) {
    if (!u.role.equals("CITIZEN"))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta função é da conta de cidadão.");
  }

  static void password(String value) {
    if (value == null || value.length() < 12 || value.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Use uma senha de 12 a 72 bytes. Prefira uma frase longa e exclusiva.");
  }

  @Transactional
  AppUser register(String name, String email, String password, String role, String phone) {
    password(password);
    email = email.strip().toLowerCase(Locale.ROOT);
    if (users.findByEmail(email).isPresent())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Não foi possível criar a conta com este e-mail. Se já possui conta, entre ou recupere o"
              + " acesso.");
    if (!List.of("CITIZEN", "SUPPORTER").contains(role))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha cidadão ou familiar.");
    AppUser u = new AppUser();
    u.name = name.strip();
    u.email = email;
    u.passwordHash = passwords.encode(password);
    u.role = role;
    u.createdAt = Instant.now();
    users.saveAndFlush(u);
    u.phoneCipher = crypto.encrypt(phone, "phone:" + u.id);
    return users.save(u);
  }

  Map<String, Object> view(AppUser u) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", u.id);
    m.put("name", u.name);
    m.put("email", u.email);
    m.put("role", u.role);
    m.put("phone", crypto.decrypt(u.phoneCipher, "phone:" + u.id));
    m.put("autoShareHigh", u.autoShareHigh);
    return m;
  }
}
