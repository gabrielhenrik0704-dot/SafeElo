package br.com.safeelo;

import java.time.*;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class ResetService {
  final ResetRepository resets;
  final AccountService accounts;
  final NotificationService mail;
  final SessionRegistry sessions;

  ResetService(ResetRepository r, AccountService a, NotificationService m, SessionRegistry s) {
    resets = r;
    accounts = a;
    mail = m;
    sessions = s;
  }

  @Transactional
  void request(String email) {
    if (!mail.configured())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "A recuperação por e-mail ainda não está configurada neste servidor. Contate o"
              + " responsável pela instalação.");
    accounts
        .users()
        .findByEmail(email.strip().toLowerCase(Locale.ROOT))
        .ifPresent(
            u -> {
              resets.deleteByUserId(u.id);
              String token = accounts.crypto().token();
              PasswordReset r = new PasswordReset();
              r.userId = u.id;
              r.tokenHash = CryptoService.hash(token);
              r.expiresAt = Instant.now().plus(Duration.ofMinutes(30));
              resets.save(r);
              boolean sent =
                  mail.send(
                      u.email,
                      "SafeElo: recuperação de acesso",
                      "Se você pediu para trocar a senha, abra o endereço a seguir. Ele expira em"
                          + " 30 minutos e funciona uma vez.\n"
                          + mail.base
                          + "/login#redefinir="
                          + token
                          + "\n"
                          + "Se não foi você, ignore esta mensagem. Nunca envie sua senha a"
                          + " ninguém.");
              if (!sent)
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "O serviço de e-mail está indisponível. Tente novamente mais tarde.");
            });
  }

  @Transactional
  void apply(String token, String password) {
    AccountService.password(password);
    PasswordReset r =
        resets
            .findByTokenHash(CryptoService.hash(token))
            .filter(x -> !x.used && x.expiresAt.isAfter(Instant.now()))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Link inválido, usado ou expirado. Solicite outro."));
    AppUser u = accounts.users().findById(r.userId).orElseThrow();
    u.passwordHash = accounts.passwords().encode(password);
    accounts.users().save(u);
    r.used = true;
    resets.save(r);
    for (Object p : sessions.getAllPrincipals())
      if (p instanceof UserDetails d && d.getUsername().equals(u.email))
        sessions.getAllSessions(p, false).forEach(s -> s.expireNow());
  }
}
