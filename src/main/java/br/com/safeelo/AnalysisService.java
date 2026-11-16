package br.com.safeelo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AnalysisService {
  final AnalysisRepository analyses;
  final RiskEngine engine;
  final CryptoService crypto;
  final ObjectMapper json;
  final EloService elo;

  AnalysisService(
      AnalysisRepository r, RiskEngine e, CryptoService c, ObjectMapper j, EloService l) {
    analyses = r;
    engine = e;
    crypto = c;
    json = j;
    elo = l;
  }

  AnalysisRepository analyses() {
    return analyses;
  }

  @Transactional
  Map<String, Object> create(AppUser u, String kind, String input) {
    RiskEngine.Result result = engine.analyze(kind, input);
    ThreatAnalysis a = new ThreatAnalysis();
    a.userId = u.id;
    a.kind = kind;
    a.contentCipher = crypto.encrypt(engine.clean(input), "analysis:" + u.id);
    a.score = result.score();
    a.level = result.level();
    a.createdAt = Instant.now();
    try {
      a.reasonsJson = json.writeValueAsString(result.reasons());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    analyses.saveAndFlush(a);
    int shared = u.autoShareHigh && a.level.equals("HIGH") ? elo.share(a) : 0;
    Map<String, Object> view = view(a, true);
    view.put("newAlerts", shared);
    return view;
  }

  ThreatAnalysis owned(AppUser u, Long id) {
    return analyses
        .findByIdAndUserId(id, u.id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verificação não encontrada."));
  }

  Map<String, Object> view(ThreatAnalysis a, boolean content) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.id);
    m.put("kind", a.kind);
    m.put("score", a.score);
    m.put("level", a.level);
    m.put("createdAt", a.createdAt);
    try {
      m.put("reasons", json.readValue(a.reasonsJson, List.class));
    } catch (Exception e) {
      m.put("reasons", List.of());
    }
    m.put(
        "limitation",
        "Análise por regras, sem abrir links, consultar reputação ao vivo ou confirmar"
            + " identidade/beneficiário. Um resultado não garante segurança.");
    m.put(
        "actions",
        a.level.equals("HIGH")
            ? List.of(
                "Não faça pagamentos ou informe dados agora.",
                "Confirme o pedido por outro canal conhecido.",
                "Peça ajuda ao seu Elo antes de decidir.")
            : List.of(
                "Confira remetente e contexto por um canal conhecido.",
                "Não compartilhe senhas ou códigos de acesso.",
                "Em pagamentos, confira o beneficiário no aplicativo oficial."));
    if (content) m.put("content", crypto.decrypt(a.contentCipher, "analysis:" + a.userId));
    return m;
  }
}
