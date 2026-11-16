# Arquitetura — SafeElo

Aplicação monolítica independente. O mesmo processo Java serve a interface HTML/CSS/JS e a API, reduzindo complexidade de CORS e implantação. Não depende de ChatGPT, OpenAI ou do login de uma plataforma de desenvolvimento.

## Camadas

- Spring Boot 3.5.7 / Java 17: HTTP, validação, regras, transações e persistência.
- Spring Security: BCrypt custo 12, sessão própria, cookie HttpOnly/SameSite=Lax, rotação no login, CSRF e regras de autorização.
- JPA/Hibernate e Flyway: consultas parametrizadas, migrações e integridade referencial.
- H2 em arquivo local para execução acadêmica; PostgreSQL 17 para implantação.
- AES-256-GCM: conteúdo das verificações e telefone cifrados; chave externa ao banco e IV aleatório por registro. Não é E2EE: o servidor lê o conteúdo para analisá-lo.
- HTML/CSS/JavaScript sem frameworks ou bibliotecas de terceiros no navegador. Fontes locais do sistema, botões grandes, foco visível, mensagens compreensíveis e alertas com texto/ícone/cor.

## Modelo relacional

APP_USER 1:N THREAT_ANALYSIS
APP_USER 1:N ELO_LINK (owner_id)
APP_USER 1:N ELO_LINK (supporter_id, opcional até aceite)
ELO_LINK 1:N SUPPORT_ALERT
THREAT_ANALYSIS 1:N SUPPORT_ALERT
APP_USER 1:N PASSWORD_RESET
RISK_RULE: regras persistidas e consultadas pelo motor.

Os nomes refletem a prévia TB_USUARIO, TB_TUTOR_APOIO, TB_ANALISE_AMEACA e TB_PADRAO_GOLPE da documentação. Cidadão e familiar são contas da mesma entidade, com papel distinto; o relacionamento e o consentimento ficam em ELO_LINK.

## Fluxo do Elo

INVITED → AWAITING_APPROVAL → ACTIVE → REVOKED.

O convite usa token aleatório de 256 bits; só o hash fica no banco. Expira em sete dias e perde validade após o aceite. O familiar precisa entrar com o e-mail indicado. Isso não verifica a propriedade do e-mail: a confirmação humana do cidadão, por canal conhecido, é uma etapa obrigatória antes de qualquer compartilhamento. Não há compartilhamento em estados pendentes ou revogados.

Uma verificação é compartilhada mediante ação explícita do cidadão ou preferência prévia autoShareHigh=true para nível HIGH. Cada par vínculo/verificação gera um alerta único. O familiar só lê alertas associados aos seus vínculos ativos. Marcar como acompanhado não transforma o conteúdo em seguro.

O aviso existe de verdade no banco e no painel. E-mail é opcional, não contém o conteúdo analisado e pode falhar independentemente do alerta interno. SMTP ausente é mostrado como não configurado, nunca como mensagem entregue. Esta versão não usa push móvel, SMS ou WhatsApp e não tenta fazer automação desses canais.

## Motor de risco

Normaliza controles e caracteres bidirecionais; aplica regras do banco para urgência, pagamento, credenciais, prêmio, ameaça e imitação de familiar. Soma pesos até 100. LOW = 0, CAUTION 1–59, HIGH ≥60. Os limiares são heurísticos, não probabilidades calibradas.

Links: estrutura URI, credenciais embutidas, HTTP, punycode, IP literal, encurtadores conhecidos e excesso de subdomínios. Não abre a URL, evitando SSRF e navegação maliciosa no servidor. Não consulta reputação em tempo real.

Boleto: 47 dígitos com verificação dos três campos por módulo 10 e dígito geral por módulo 11. Formato de arrecadação com 48 dígitos apenas identificado, sem validação completa. Não confirma cobrança ou beneficiário.

PIX: formato de e-mail, telefone brasileiro internacional, CPF/CNPJ com quantidade de dígitos e UUID. Não valida matematicamente CPF/CNPJ nem consulta DICT/Banco Central. PIX copia-e-cola/QR não é validado. Uma chave com formato válido pode pertencer a um fraudador.

Todos os níveis incluem limitações e próximos passos. Não há categoria “segurança garantida”, inteligência artificial ou análise simulada.

## Segurança e limites operacionais

- Nome e e-mail permanecem em texto no banco para identificação e login; conteúdo e telefone são cifrados. Metadados de risco/data/vínculo também são visíveis ao servidor. Não há promessa de anonimato.
- Perfis não podem ser elevados por PATCH. Autorizações de propriedade existem no backend, não só na interface.
- Tokens de convite/recuperação usam fragmentos nas URLs, removidos do endereço e mantidos apenas na memória da página. O servidor não recebe o fragmento em logs de URL.
- CSP sem scripts inline ou unsafe-eval, proteção de frame e MIME, no-store, no-referrer, CSRF. Há limitação básica de tentativas por processo/IP, não um WAF distribuído.
- Recuperação exige SMTP. Token expira em 30 minutos e funciona uma vez; as sessões anteriores são expiradas após mudança de senha.
- Exclusão do cidadão remove dados do banco ativo por cascata. Cópias externas e backups têm retenção operacional separada.
- Não há validação de identidade civil, verificação de e-mail, MFA, antivírus, captura de tela/OCR ou proteção contra todos os golpes. Revisão independente e avaliação de LGPD/retensão/contato do controlador são necessárias antes de receber dados reais em produção.
- Sessões e limitador são locais a uma instância. Escalar múltiplas instâncias requer armazenamento compartilhado de sessão e proteção de borda.
