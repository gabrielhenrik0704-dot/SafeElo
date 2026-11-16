package br.com.safeelo;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class SecurityConfig {
  @Bean
  org.springframework.security.core.session.SessionRegistry sessionRegistry() {
    return new org.springframework.security.core.session.SessionRegistryImpl();
  }

  @Bean
  org.springframework.security.web.session.HttpSessionEventPublisher sessionEvents() {
    return new org.springframework.security.web.session.HttpSessionEventPublisher();
  }

  @Bean
  PasswordEncoder encoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService users(UserRepository repo) {
    return email ->
        repo.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))
            .map(u -> User.withUsername(u.email).password(u.passwordHash).roles(u.role).build())
            .orElseThrow(() -> new UsernameNotFoundException("Conta não encontrada"));
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, org.springframework.security.core.session.SessionRegistry registry)
      throws Exception {
    var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.setCookieCustomizer(c -> c.sameSite("Lax"));
    http.csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/login",
                        "/cadastro",
                        "/app",
                        "/privacidade",
                        "/assets/**",
                        "/favicon.ico",
                        "/error",
                        "/api/csrf",
                        "/api/health",
                        "/api/auth/register",
                        "/api/auth/forgot",
                        "/api/auth/reset")
                    .permitAll()
                    .requestMatchers("/api/support/**")
                    .hasRole("SUPPORTER")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .sessionManagement(
            s ->
                s.maximumSessions(5)
                    .sessionRegistry(registry)
                    .expiredSessionStrategy(
                        e ->
                            json(
                                e.getResponse(),
                                401,
                                "{\"error\":\"Sessão expirada. Entre novamente.\"}")))
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/auth/login")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .successHandler((req, res, a) -> json(res, 200, "{\"ok\":true}"))
                    .failureHandler(
                        (req, res, e) ->
                            json(res, 401, "{\"error\":\"E-mail ou senha incorretos.\"}")))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                    .logoutSuccessHandler((req, res, a) -> json(res, 200, "{\"ok\":true}")))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, x) ->
                            json(res, 401, "{\"error\":\"Entre na sua conta para continuar.\"}"))
                    .accessDeniedHandler(
                        (req, res, x) ->
                            json(
                                res,
                                403,
                                "{\"error\":\"Acesso não permitido ou sessão de segurança expirada."
                                    + " Recarregue a página.\"}")))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; script-src-attr 'none';"
                                + " style-src 'self'; img-src 'self' data:; font-src 'self';"
                                + " connect-src 'self'; frame-ancestors 'none'; base-uri 'none';"
                                + " form-action 'self'; object-src 'none'")))
        .addFilterBefore(new AuthRateFilter(), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  static void json(HttpServletResponse r, int code, String body) throws IOException {
    r.setStatus(code);
    r.setContentType("application/json;charset=UTF-8");
    r.getWriter().write(body);
  }

  static class AuthRateFilter extends OncePerRequestFilter {
    record Counter(int count, long until) {}

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    protected void doFilterInternal(HttpServletRequest q, HttpServletResponse r, FilterChain c)
        throws ServletException, IOException {
      r.setHeader("Referrer-Policy", "no-referrer");
      r.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
      r.setHeader("Cache-Control", "no-store");
      if ("POST".equals(q.getMethod()) && q.getRequestURI().startsWith("/api/auth/")) {
        long now = System.currentTimeMillis();
        counters.entrySet().removeIf(e -> e.getValue().until < now);
        String key = q.getRemoteAddr() + q.getRequestURI();
        Counter value =
            counters.compute(
                key,
                (k, old) ->
                    old == null || old.until < now
                        ? new Counter(1, now + 300_000)
                        : new Counter(old.count + 1, old.until));
        if (value.count > 20) {
          r.setHeader("Retry-After", "300");
          json(r, 429, "{\"error\":\"Muitas tentativas. Aguarde alguns minutos.\"}");
          return;
        }
      }
      if (q.getContentLengthLong() > 32768) {
        json(r, 413, "{\"error\":\"Conteúdo acima do limite.\"}");
        return;
      }
      c.doFilter(q, r);
    }
  }
}
