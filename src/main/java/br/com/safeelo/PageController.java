package br.com.safeelo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class PageController {
  @GetMapping({"/", "/login", "/cadastro", "/app", "/privacidade"})
  String index() {
    return "forward:/assets/index.html";
  }
}
