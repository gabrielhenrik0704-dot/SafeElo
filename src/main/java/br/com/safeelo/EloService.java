package br.com.safeelo;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class EloService {
  final LinkRepository links;
  final AlertRepository alerts;
  final UserRepository users;
  final CryptoService crypto;
  final NotificationService mail;

  EloService(
      LinkRepository l,
      AlertRepository a,
      UserRepository u,
      CryptoService c,
      NotificationService n) {
    links = l;
    alerts = a;
    users = u;
    crypto = c;
    mail = n;
  }

  LinkRepository links() {
    return links;
  }

  AlertRepository alerts() {
    return alerts;
  }

  UserRepository users() {
    return users;
  }

  Map<String, Object> linkView(EloLink l) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", l.id);
    m.put("email", l.supportEmail);
    m.put("relationship", l.relationshipLabel);
    m.put("state", l.state);
    m.put("expiresAt", l.expiresAt == null ? "" : l.expiresAt.toString());
    m.put("ownerName", users.findById(l.ownerId).map(x -> x.name).orElse("Conta removida"));
    m.put(
        "supporterName",
        l.supporterId == null ? "" : users.findById(l.supporterId).map(x -> x.name).orElse(""));
    return m;
  }

  @Transactional
  Map<String, Object> invite(AppUser owner, String email, String relation) {
    email = email.strip().toLowerCase(Locale.ROOT);
    if (owner.email.equals(email))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Convide outra pessoa.");
    if (links.findByOwnerIdAndState(owner.id, "ACTIVE").size() >= 5)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limite de cinco vínculos ativos.");
    EloLink l = links.findByOwnerIdAndSupportEmail(owner.id, email).orElseGet(EloLink::new);
    if (l.id != null
        && !l.state.equals("REVOKED")
        && !(l.state.equals("INVITED") && l.expiresAt.isBefore(Instant.now())))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Já existe um vínculo ou convite para este e-mail.");
    if (l.id != null) alerts.deleteByLinkId(l.id);
    String token = crypto.token();
    l.ownerId = owner.id;
    l.supporterId = null;
    l.supportEmail = email;
    l.relationshipLabel = relation;
    l.state = "INVITED";
    l.tokenHash = CryptoService.hash(token);
    l.expiresAt = Instant.now().plus(Duration.ofDays(7));
    l.createdAt = Instant.now();
    links.save(l);
    return Map.of(
        "link",
        linkView(l),
        "token",
        token,
        "instructions",
        "Envie este convite por um canal privado conhecido. O familiar aceita com a conta do e-mail"
            + " indicado; depois você confirma o vínculo. O token é mostrado apenas agora.");
  }

  @Transactional
  Map<String, Object> accept(AppUser supporter, String token) {
    if (!supporter.role.equals("SUPPORTER"))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Entre com uma conta de familiar para aceitar.");
    EloLink l =
        links
            .findByTokenHash(CryptoService.hash(token))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Convite inválido ou já utilizado."));
    if (!l.state.equals("INVITED") || l.expiresAt.isBefore(Instant.now()))
      throw new ResponseStatusException(
          HttpStatus.GONE, "Convite expirado. Peça outro ao cidadão.");
    if (!l.supportEmail.equals(supporter.email))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Entre com o e-mail indicado no convite.");
    l.supporterId = supporter.id;
    l.tokenHash = null;
    l.state = "AWAITING_APPROVAL";
    links.save(l);
    return linkView(l);
  }

  @Transactional
  void approve(AppUser owner, Long id) {
    EloLink l = owned(owner, id);
    if (!l.state.equals("AWAITING_APPROVAL"))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "O familiar precisa aceitar primeiro.");
    l.state = "ACTIVE";
    links.save(l);
  }

  EloLink owned(AppUser owner, Long id) {
    return links
        .findById(id)
        .filter(l -> l.ownerId.equals(owner.id))
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vínculo não encontrado."));
  }

  @Transactional
  void revoke(AppUser user, Long id) {
    EloLink l =
        links
            .findById(id)
            .filter(x -> x.ownerId.equals(user.id) || user.id.equals(x.supporterId))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vínculo não encontrado."));
    l.state = "REVOKED";
    l.tokenHash = null;
    links.save(l);
  }

  @Transactional
  int share(ThreatAnalysis a) {
    List<EloLink> active = links.findByOwnerIdAndState(a.userId, "ACTIVE");
    int count = 0;
    for (EloLink l : active) {
      if (alerts.findByLinkIdAndAnalysisId(l.id, a.id).isPresent()) continue;
      SupportAlert alert = new SupportAlert();
      alert.linkId = l.id;
      alert.analysisId = a.id;
      alert.state = "PENDING";
      alert.createdAt = Instant.now();
      alert.emailState = mail.notifySupport(l.supportEmail);
      alerts.save(alert);
      count++;
    }
    return count;
  }

  List<EloLink> activeForSupporter(AppUser u) {
    return links.findBySupporterIdOrderByCreatedAtDesc(u.id).stream()
        .filter(l -> l.state.equals("ACTIVE"))
        .toList();
  }
}
