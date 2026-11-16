package br.com.safeelo;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
class NotificationService {
  final ObjectProvider<JavaMailSender> sender;
  final String host, from, base;

  NotificationService(
      ObjectProvider<JavaMailSender> sender,
      @Value("${spring.mail.host:}") String host,
      @Value("${safeelo.mail-from:}") String from,
      @Value("${safeelo.base-url}") String base) {
    this.sender = sender;
    this.host = host;
    this.from = from;
    this.base = base;
  }

  boolean configured() {
    return !host.isBlank() && !from.isBlank() && sender.getIfAvailable() != null;
  }

  boolean send(String recipient, String subject, String body) {
    if (!configured()) return false;
    try {
      SimpleMailMessage m = new SimpleMailMessage();
      m.setFrom(from);
      m.setTo(recipient);
      m.setSubject(subject);
      m.setText(body);
      sender.getObject().send(m);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  String notifySupport(String email) {
    if (!configured()) return "NOT_CONFIGURED";
    return send(
            email,
            "SafeElo: uma pessoa pediu sua ajuda",
            "Há uma nova verificação compartilhada com você. Entre na sua conta para revisar: "
                + base
                + "/app\nO SafeElo não realiza pagamentos e nunca pede senhas por e-mail.")
        ? "SENT"
        : "FAILED";
  }
}
