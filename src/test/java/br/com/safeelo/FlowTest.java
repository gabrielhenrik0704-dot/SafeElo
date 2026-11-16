package br.com.safeelo;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:safeelo_test;DB_CLOSE_DELAY=-1",
      "safeelo.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "safeelo.base-url=http://localhost:8080",
      "spring.mail.host=",
      "safeelo.mail-from="
    })
class FlowTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AnalysisRepository analyses;
  @Autowired UserRepository users;
  @Autowired CryptoService crypto;
  @Autowired ResetRepository resets;
  @Autowired RiskEngine engine;
  static final String PASS = "UmaSenhaForte-2026!";

  String email() {
    return UUID.randomUUID() + "@example.org";
  }

  void register(String email, String role) throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name",
                            role.equals("CITIZEN") ? "Cidadão Teste" : "Familiar Teste",
                            "email",
                            email,
                            "password",
                            PASS,
                            "role",
                            role,
                            "phone",
                            "11999999999",
                            "consent",
                            true))))
        .andExpect(status().isCreated());
  }

  MockHttpSession login(String email, String password) throws Exception {
    return (MockHttpSession)
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .param("email", email)
                    .param("password", password))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession(false);
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  @Test
  void completeCitizenSupportFlowAndPrivacy() throws Exception {
    String citizen = email(), support = email(), other = email();
    register(citizen, "CITIZEN");
    register(support, "SUPPORTER");
    register(other, "SUPPORTER");
    var c = login(citizen, PASS);
    var s = login(support, PASS);
    var x = login(other, PASS);
    var invitation =
        body(
            mvc.perform(
                    post("/api/elos/invite")
                        .session(c)
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of("email", support, "relationship", "Filha"))))
                .andExpect(status().isCreated())
                .andReturn());
    long link = invitation.path("link").path("id").asLong();
    String token = invitation.path("token").asText();
    assertFalse(token.isBlank());
    mvc.perform(
            post("/api/elos/accept")
                .session(x)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", token))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/elos/accept")
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", token))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("AWAITING_APPROVAL"));
    var result =
        body(
            mvc.perform(
                    post("/api/analyses")
                        .session(c)
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "kind",
                                    "TEXT",
                                    "content",
                                    "Mãe, faça um PIX agora. É urgente. Conteúdo PRIVADO 7294"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value("HIGH"))
                .andReturn());
    long id = result.path("id").asLong();
    mvc.perform(post("/api/analyses/" + id + "/help").session(c).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/support/alerts").session(s))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    mvc.perform(post("/api/elos/" + link + "/approve").session(c).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(post("/api/analyses/" + id + "/help").session(c).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newAlerts").value(1));
    mvc.perform(post("/api/analyses/" + id + "/help").session(c).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newAlerts").value(0));
    var list =
        body(
            mvc.perform(get("/api/support/alerts").session(s))
                .andExpect(status().isOk())
                .andReturn());
    assertEquals(1, list.size());
    assertTrue(list.get(0).path("analysis").path("content").asText().contains("PRIVADO"));
    long alert = list.get(0).path("id").asLong();
    mvc.perform(get("/api/analyses/" + id).session(s)).andExpect(status().isNotFound());
    mvc.perform(get("/api/support/alerts").session(x)).andExpect(content().json("[]"));
    mvc.perform(post("/api/support/alerts/" + alert + "/acknowledge").session(x).with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/support/alerts/" + alert + "/acknowledge").session(s).with(csrf()))
        .andExpect(status().isOk());
    ThreatAnalysis stored = analyses.findById(id).orElseThrow();
    assertFalse(stored.contentCipher.contains("PRIVADO"));
    assertTrue(stored.contentCipher.startsWith("v1:"));
    assertTrue(users.findByEmail(citizen).orElseThrow().passwordHash.startsWith("$2"));
    mvc.perform(delete("/api/elos/" + link).session(c).with(csrf())).andExpect(status().isOk());
    mvc.perform(get("/api/support/alerts").session(s)).andExpect(content().json("[]"));
    mvc.perform(delete("/api/analyses/" + id).session(c).with(csrf())).andExpect(status().isOk());
    assertFalse(analyses.existsById(id));
  }

  @Test
  void authenticationCsrfValidationAndOwnership() throws Exception {
    mvc.perform(get("/api/analyses")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    String a = email(), b = email();
    register(a, "CITIZEN");
    register(b, "CITIZEN");
    var sa = login(a, PASS);
    var sb = login(b, PASS);
    mvc.perform(post("/api/auth/login").with(csrf()).param("email", a).param("password", "wrong"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/analyses")
                .session(sa)
                .with(csrf())
                .contentType("application/json")
                .content("{\"kind\":\"SHELL\",\"content\":\"test\"}"))
        .andExpect(status().isBadRequest());
    var row =
        body(
            mvc.perform(
                    post("/api/analyses")
                        .session(sa)
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                            "{\"kind\":\"TEXT\",\"content\":\"Olá, nossa reunião está marcada para"
                                + " amanhã.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value("LOW"))
                .andReturn());
    long id = row.path("id").asLong();
    mvc.perform(get("/api/analyses/" + id).session(sb)).andExpect(status().isNotFound());
    mvc.perform(delete("/api/analyses/" + id).session(sb).with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/support/alerts").session(sa)).andExpect(status().isForbidden());
    mvc.perform(post("/api/auth/logout").session(sa).with(csrf())).andExpect(status().isOk());
  }

  @Test
  void resetIsSingleUseExpiresOldSessionAndAccountDeletion() throws Exception {
    String email = email();
    register(email, "CITIZEN");
    var old = login(email, PASS);
    AppUser u = users.findByEmail(email).orElseThrow();
    String token = crypto.token();
    PasswordReset r = new PasswordReset();
    r.userId = u.id;
    r.tokenHash = CryptoService.hash(token);
    r.expiresAt = Instant.now().plusSeconds(600);
    resets.save(r);
    mvc.perform(
            post("/api/auth/forgot")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email))))
        .andExpect(status().isServiceUnavailable());
    mvc.perform(
            post("/api/auth/reset")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("token", token, "password", "OutraSenhaSegura-2026!"))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/me").session(old)).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/reset")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("token", token, "password", "OutraSenhaSegura-2026!"))))
        .andExpect(status().isBadRequest());
    var current = login(email, "OutraSenhaSegura-2026!");
    mvc.perform(
            delete("/api/me")
                .session(current)
                .with(csrf())
                .contentType("application/json")
                .content("{\"password\":\"OutraSenhaSegura-2026!\"}"))
        .andExpect(status().isOk());
    assertTrue(users.findByEmail(email).isEmpty());
  }

  @Test
  void cryptographyAndRiskRules() {
    assertEquals("CAUTION", engine.analyze("LINK", "http://example.com").level());
    String first = crypto.encrypt("segredo", "test:1"),
        second = crypto.encrypt("segredo", "test:1");
    assertNotEquals(first, second);
    assertEquals("segredo", crypto.decrypt(first, "test:1"));
    assertThrows(IllegalStateException.class, () -> crypto.decrypt(first, "test:2"));
    assertEquals(
        "HIGH",
        engine
            .analyze(
                "TEXT", "Sua conta será bloqueada. Envie a senha agora para fazer o PIX urgente.")
            .level());
    assertTrue(engine.analyze("LINK", "http://banco.test@192.168.0.1/pagar").score() >= 60);
    assertEquals("CAUTION", engine.analyze("BOLETO", "12345").level());
    assertTrue(engine.analyze("PIX", "chave incorreta").score() >= 25);
  }

  @Autowired LinkRepository links;

  @Test
  void highRiskAutomaticSharingNeedsOptInAndActiveLink() throws Exception {
    String citizen = email(), support = email();
    register(citizen, "CITIZEN");
    register(support, "SUPPORTER");
    var c = login(citizen, PASS);
    var f = login(support, PASS);
    AppUser owner = users.findByEmail(citizen).orElseThrow(),
        helper = users.findByEmail(support).orElseThrow();
    EloLink link = new EloLink();
    link.ownerId = owner.id;
    link.supporterId = helper.id;
    link.supportEmail = support;
    link.relationshipLabel = "Pessoa de confiança";
    link.state = "ACTIVE";
    link.createdAt = Instant.now();
    links.save(link);
    String high = "{\"kind\":\"TEXT\",\"content\":\"Mãe, faça PIX agora urgente\"}";
    mvc.perform(
            post("/api/analyses")
                .session(c)
                .with(csrf())
                .contentType("application/json")
                .content(high))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.newAlerts").value(0));
    mvc.perform(
            patch("/api/me")
                .session(c)
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Cidadão Teste\",\"phone\":\"\",\"autoShareHigh\":true}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/analyses")
                .session(c)
                .with(csrf())
                .contentType("application/json")
                .content(high))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.newAlerts").value(1));
    mvc.perform(get("/api/support/alerts").session(f))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(delete("/api/elos/" + link.id).session(c).with(csrf())).andExpect(status().isOk());
    mvc.perform(
            post("/api/analyses")
                .session(c)
                .with(csrf())
                .contentType("application/json")
                .content(high))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.newAlerts").value(0));
  }
}
