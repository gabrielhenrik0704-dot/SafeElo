package br.com.safeelo;

import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
class DeploymentConfig implements ApplicationRunner {
  private final Environment environment;
  private final String base;

  DeploymentConfig(Environment e, @Value("${safeelo.base-url}") String b) {
    environment = e;
    base = b;
  }

  public void run(ApplicationArguments args) {
    URI u;
    try {
      u = URI.create(base);
      if (u.getHost() == null
          || u.getUserInfo() != null
          || u.getQuery() != null
          || u.getFragment() != null) throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new IllegalStateException(
          "Configure APP_BASE_URL com a origem da aplicação, sem credenciais ou parâmetros.");
    }
    if (Arrays.asList(environment.getActiveProfiles()).contains("prod")
        && !u.getScheme().equals("https"))
      throw new IllegalStateException("O perfil prod exige APP_BASE_URL HTTPS.");
  }
}
