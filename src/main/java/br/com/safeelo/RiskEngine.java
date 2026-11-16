package br.com.safeelo;

import java.net.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
class RiskEngine {
  record Result(
      int score, String level, List<String> reasons, List<String> actions, String limitation) {}

  final RuleRepository rules;

  RiskEngine(RuleRepository r) {
    rules = r;
  }

  String clean(String s) {
    return s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
        .replaceAll("[\u202A-\u202E\u2066-\u2069]", "")
        .strip();
  }

  Result analyze(String kind, String content) {
    String text = clean(content),
        fold =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    int score = 0;
    List<String> reasons = new ArrayList<>();
    for (RiskRule rule : rules.findAll()) {
      if (Pattern.compile(rule.expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
          .matcher(fold)
          .find()) {
        score += rule.weight;
        reasons.add(rule.explanation);
      }
    }
    Matcher links = Pattern.compile("(?i)https?://[^\\s<>\"']+").matcher(text);
    while (links.find()) {
      String link = links.group().replaceAll("[.,;)]+$", "");
      try {
        URI uri = URI.create(link);
        String host = uri.getHost();
        if (host == null) {
          score += 25;
          add(reasons, "O endereço contém um domínio que não conseguimos interpretar.");
          continue;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (uri.getUserInfo() != null) {
          score += 45;
          add(reasons, "O link usa texto antes de @, o que pode esconder o destino real.");
        }
        if (uri.getScheme().equalsIgnoreCase("http")) {
          score += 15;
          add(reasons, "O endereço não usa HTTPS. Isso reduz a proteção da conexão.");
        }
        if (host.contains("xn--")) {
          score += 25;
          add(
              reasons,
              "O domínio usa caracteres internacionalizados. Confira se imita um nome conhecido.");
        }
        if (host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || host.equals("localhost")) {
          score += 25;
          add(reasons, "O endereço usa um IP ou nome local em vez de um domínio identificável.");
        }
        if (Set.of("bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly").contains(host)) {
          score += 20;
          add(reasons, "O link está encurtado e oculta o endereço final.");
        }
        if (host.split("\\.").length > 4) {
          score += 15;
          add(reasons, "O endereço possui muitos subdomínios. Confira o domínio principal.");
        }
      } catch (Exception e) {
        score += 25;
        add(reasons, "O link possui uma estrutura incomum ou inválida.");
      }
    }
    if (kind.equals("LINK") && !fold.matches("https?://\\S+")) {
      score += 30;
      add(reasons, "Informe uma URL completa iniciando com https:// ou http://.");
    }
    if (kind.equals("BOLETO")) {
      String digits = text.replaceAll("[.\\s-]", "");
      if (digits.matches("\\d{47}")) {
        if (!validBankSlip(digits)) {
          score += 50;
          add(reasons, "A linha digitável não passou na checagem de dígitos verificadores.");
        } else
          add(
              reasons,
              "Os dígitos verificadores são consistentes. Isso não confirma beneficiário, cobrança"
                  + " ou legitimidade.");
      } else if (digits.matches("\\d{48}")) {
        score += 25;
        add(
            reasons,
            "Linha de arrecadação com 48 dígitos: esta versão verifica o formato, mas não valida"
                + " emissor nem todos os dígitos verificadores.");
      } else {
        score += 40;
        add(
            reasons,
            "Não reconhecemos uma linha digitável de 47 ou 48 dígitos. Confira o documento.");
      }
    }
    if (kind.equals("PIX")) {
      boolean format =
          text.matches("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")
              || text.matches("\\+55\\d{10,11}")
              || text.matches("\\d{11}|\\d{14}")
              || text.matches("(?i)[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");
      if (!format) {
        score += 30;
        add(
            reasons,
            "A informação não corresponde aos formatos comuns de chave PIX. Não validamos chaves"
                + " copia-e-cola nesta versão.");
      } else
        add(
            reasons,
            "O formato da chave é reconhecível. Não consultamos o Banco Central nem confirmamos seu"
                + " titular.");
    }
    score = Math.min(100, score);
    String level = score >= 60 ? "HIGH" : score > 0 ? "CAUTION" : "LOW";
    if (reasons.isEmpty())
      reasons.add(
          "Não encontramos os padrões comuns avaliados por esta versão. Isso não comprova"
              + " segurança.");
    List<String> actions =
        level.equals("HIGH")
            ? List.of(
                "Não faça o pagamento ou informe dados agora.",
                "Confirme o pedido por outro canal conhecido.",
                "Peça ajuda ao seu Elo antes de decidir.")
            : level.equals("CAUTION")
                ? List.of(
                    "Confira remetente, endereço e beneficiário por um canal oficial.",
                    "Não compartilhe senhas ou códigos de acesso.",
                    "Se tiver dúvida, peça ajuda ao seu Elo.")
                : List.of(
                    "Confira o contexto antes de agir.",
                    "Em pagamentos, confirme o beneficiário no aplicativo oficial.",
                    "Desconfie de pedidos de senha, códigos ou urgência.");
    return new Result(
        score,
        level,
        reasons,
        actions,
        "Análise por regras e padrões, sem abrir links, consultar reputação ao vivo ou confirmar"
            + " identidade/beneficiário. Não é garantia de segurança nem detecção definitiva de"
            + " fraude.");
  }

  static void add(List<String> list, String s) {
    if (!list.contains(s)) list.add(s);
  }

  static int mod10(String s) {
    int sum = 0, w = 2;
    for (int i = s.length() - 1; i >= 0; i--) {
      int v = (s.charAt(i) - '0') * w;
      sum += v > 9 ? v - 9 : v;
      w = w == 2 ? 1 : 2;
    }
    return (10 - sum % 10) % 10;
  }

  static boolean validBankSlip(String s) {
    if (mod10(s.substring(0, 9)) != s.charAt(9) - '0'
        || mod10(s.substring(10, 20)) != s.charAt(20) - '0'
        || mod10(s.substring(21, 31)) != s.charAt(31) - '0') return false;
    String bar =
        s.substring(0, 4)
            + s.charAt(32)
            + s.substring(33)
            + s.substring(4, 9)
            + s.substring(10, 20)
            + s.substring(21, 31);
    int sum = 0, w = 2;
    for (int i = 43; i >= 0; i--) {
      if (i == 4) continue;
      sum += (bar.charAt(i) - '0') * w;
      w = w == 9 ? 2 : w + 1;
    }
    int dv = 11 - sum % 11;
    if (dv == 0 || dv == 10 || dv == 11) dv = 1;
    return dv == bar.charAt(4) - '0';
  }
}

@Configuration
class RuleSeeds {
  @Bean
  CommandLineRunner seedRules(RuleRepository repo) {
    return args -> {
      if (repo.count() > 0) return;
      Object[][] seeds = {
        {
          "urgencia",
          "\\b(urgente|agora|imediatamente|ultim[oa] aviso|hoje ainda)\\b",
          "A mensagem pressiona você a agir com urgência.",
          25
        },
        {
          "pagamento",
          "\\b(pix|transferencia|deposito|pague|pagamento)\\b",
          "Há um pedido ou referência a pagamento. Confirme destinatário e contexto.",
          20
        },
        {
          "credenciais",
          "\\b(senha|codigo de verificacao|token|codigo de seguranca)\\b",
          "Há referência a senha ou código de acesso, informações que não devem ser"
              + " compartilhadas.",
          35
        },
        {
          "premio",
          "\\b(premio|sortead[oa]|ganhou|resgate seu|dinheiro facil)\\b",
          "A mensagem promete prêmio ou vantagem. Verifique as condições por canal oficial.",
          25
        },
        {
          "ameaca",
          "\\b(bloquead[oa]|suspens[oa]|cancelamento|conta sera|multa)\\b",
          "Há uma ameaça de bloqueio, suspensão ou cobrança para pressionar a decisão.",
          20
        },
        {
          "familiar",
          "\\b(mae|pai|troquei de numero|novo numero|sou seu filho|sou sua filha)\\b",
          "O texto pode se passar por um familiar. Confirme a identidade por outro canal.",
          15
        }
      };
      for (Object[] s : seeds) {
        RiskRule r = new RiskRule();
        r.category = (String) s[0];
        r.expression = (String) s[1];
        r.explanation = (String) s[2];
        r.weight = (Integer) s[3];
        repo.save(r);
      }
    };
  }
}
