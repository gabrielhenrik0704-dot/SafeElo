package br.com.safeelo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class ApiController {
  final AccountService accounts;
  final AnalysisService analysis;
  final EloService elo;
  final NotificationService mail;
  final ResetService reset;

  ApiController(
      AccountService a, AnalysisService s, EloService e, NotificationService m, ResetService r) {
    accounts = a;
    analysis = s;
    elo = e;
    mail = m;
    reset = r;
  }

  record Register(
      @NotBlank @Size(min = 2, max = 100) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank String password,
      @NotBlank String role,
      @Size(max = 30) String phone,
      @AssertTrue(message = "Aceite os termos") boolean consent) {}

  record Submission(
      @NotBlank @Pattern(regexp = "TEXT|LINK|BOLETO|PIX") String kind,
      @NotBlank @Size(max = 8000) String content) {}

  record Invite(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 60) String relationship) {}

  record Token(@NotBlank @Size(min = 40, max = 60) String token) {}

  record Profile(
      @NotBlank @Size(min = 2, max = 100) String name,
      @Size(max = 30) String phone,
      boolean autoShareHigh) {}

  record Forgot(@Email @NotBlank @Size(max = 254) String email) {}

  record Reset(@NotBlank @Size(min = 40, max = 60) String token, @NotBlank String password) {}

  record DeleteAccount(@NotBlank String password) {}

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken t) {
    return Map.of("token", t.getToken(), "headerName", t.getHeaderName());
  }

  @GetMapping("/health")
  Map<String, Object> health() {
    return Map.of(
        "service",
        "SafeElo",
        "status",
        "UP",
        "analysis",
        "heuristic",
        "emailConfigured",
        mail.configured());
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> register(@Valid @RequestBody Register r) {
    AppUser u = accounts.register(r.name, r.email, r.password, r.role, r.phone);
    return Map.of(
        "created", true, "message", "Conta criada. Entre com seu e-mail e senha.", "role", u.role);
  }

  @PostMapping("/auth/forgot")
  Map<String, String> forgot(@Valid @RequestBody Forgot r) {
    reset.request(r.email);
    return Map.of(
        "message",
        "Se existir uma conta com este e-mail, enviaremos um link de recuperação. Confira também o"
            + " spam.");
  }

  @PostMapping("/auth/reset")
  Map<String, String> reset(@Valid @RequestBody Reset r, HttpServletRequest request) {
    reset.apply(r.token, r.password);
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    return Map.of("message", "Senha atualizada. Entre novamente.");
  }

  @GetMapping("/me")
  Map<String, Object> me(Authentication a) {
    Map<String, Object> m = accounts.view(accounts.current(a));
    m.put("emailConfigured", mail.configured());
    return m;
  }

  @PatchMapping("/me")
  @Transactional
  Map<String, Object> profile(Authentication a, @Valid @RequestBody Profile r) {
    AppUser u = accounts.current(a);
    u.name = r.name.strip();
    u.phoneCipher = accounts.crypto().encrypt(r.phone, "phone:" + u.id);
    u.autoShareHigh = u.role.equals("CITIZEN") && r.autoShareHigh;
    accounts.users().save(u);
    return accounts.view(u);
  }

  @DeleteMapping("/me")
  @Transactional
  Map<String, Boolean> delete(
      Authentication a, @Valid @RequestBody DeleteAccount r, HttpServletRequest request) {
    AppUser u = accounts.current(a);
    if (!accounts.passwords().matches(r.password, u.passwordHash))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha incorreta.");
    accounts.users().delete(u);
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    return Map.of("deleted", true);
  }

  @PostMapping("/analyses")
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> create(Authentication a, @Valid @RequestBody Submission r) {
    AppUser u = accounts.current(a);
    accounts.citizen(u);
    return analysis.create(u, r.kind, r.content);
  }

  @GetMapping("/analyses")
  Map<String, Object> history(Authentication a, @RequestParam(defaultValue = "0") int page) {
    AppUser u = accounts.current(a);
    if (page < 0 || page > 10000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida.");
    var p = analysis.analyses().findByUserIdOrderByCreatedAtDesc(u.id, PageRequest.of(page, 12));
    return Map.of(
        "items",
        p.getContent().stream().map(x -> analysis.view(x, false)).toList(),
        "page",
        page,
        "totalPages",
        p.getTotalPages(),
        "total",
        p.getTotalElements());
  }

  @GetMapping("/analyses/{id}")
  Map<String, Object> detail(Authentication a, @PathVariable Long id) {
    return analysis.view(analysis.owned(accounts.current(a), id), true);
  }

  @DeleteMapping("/analyses/{id}")
  @Transactional
  Map<String, Boolean> remove(Authentication a, @PathVariable Long id) {
    analysis.analyses().delete(analysis.owned(accounts.current(a), id));
    return Map.of("deleted", true);
  }

  @PostMapping("/analyses/{id}/help")
  @Transactional
  Map<String, Object> help(Authentication a, @PathVariable Long id) {
    AppUser u = accounts.current(a);
    ThreatAnalysis row = analysis.owned(u, id);
    accounts.citizen(u);
    if (elo.links().findByOwnerIdAndState(u.id, "ACTIVE").isEmpty())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Conecte e confirme uma pessoa de confiança em Meu Elo primeiro.");
    int count = elo.share(row);
    return Map.of(
        "newAlerts",
        count,
        "message",
        count > 0
            ? "Pedido de ajuda registrado no painel do seu Elo."
            : "Esta verificação já foi compartilhada com seu Elo.",
        "emailConfigured",
        mail.configured());
  }

  @GetMapping("/elos")
  List<Map<String, Object>> links(Authentication a) {
    AppUser u = accounts.current(a);
    return (u.role.equals("CITIZEN")
            ? elo.links().findByOwnerIdOrderByCreatedAtDesc(u.id)
            : elo.links().findBySupporterIdOrderByCreatedAtDesc(u.id))
        .stream().map(elo::linkView).toList();
  }

  @PostMapping("/elos/invite")
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> invite(Authentication a, @Valid @RequestBody Invite r) {
    AppUser u = accounts.current(a);
    accounts.citizen(u);
    return elo.invite(u, r.email, r.relationship);
  }

  @PostMapping("/elos/accept")
  Map<String, Object> accept(Authentication a, @Valid @RequestBody Token r) {
    return elo.accept(accounts.current(a), r.token);
  }

  @PostMapping("/elos/{id}/approve")
  Map<String, Boolean> approve(Authentication a, @PathVariable Long id) {
    AppUser u = accounts.current(a);
    accounts.citizen(u);
    elo.approve(u, id);
    return Map.of("approved", true);
  }

  @DeleteMapping("/elos/{id}")
  Map<String, Boolean> revoke(Authentication a, @PathVariable Long id) {
    elo.revoke(accounts.current(a), id);
    return Map.of("revoked", true);
  }

  @GetMapping("/support/alerts")
  List<Map<String, Object>> alerts(Authentication a) {
    AppUser u = accounts.current(a);
    List<EloLink> links = elo.activeForSupporter(u);
    if (links.isEmpty()) return List.of();
    Map<Long, EloLink> byId = new HashMap<>();
    links.forEach(l -> byId.put(l.id, l));
    return elo
        .alerts()
        .findByLinkIdInOrderByCreatedAtDesc(links.stream().map(l -> l.id).toList())
        .stream()
        .limit(100)
        .map(
            alert -> {
              EloLink l = byId.get(alert.linkId);
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", alert.id);
              m.put("state", alert.state);
              m.put("createdAt", alert.createdAt);
              m.put("emailState", alert.emailState);
              m.put("ownerName", elo.users().findById(l.ownerId).orElseThrow().name);
              m.put(
                  "analysis",
                  analysis.view(
                      analysis.analyses().findById(alert.analysisId).orElseThrow(), true));
              return m;
            })
        .toList();
  }

  @PostMapping("/support/alerts/{id}/acknowledge")
  @Transactional
  Map<String, Boolean> acknowledge(Authentication a, @PathVariable Long id) {
    AppUser u = accounts.current(a);
    SupportAlert alert =
        elo.alerts()
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    elo.links()
        .findById(alert.linkId)
        .filter(l -> u.id.equals(l.supporterId) && l.state.equals("ACTIVE"))
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    alert.state = "ACKNOWLEDGED";
    alert.acknowledgedAt = Instant.now();
    elo.alerts().save(alert);
    return Map.of("acknowledged", true);
  }
}
