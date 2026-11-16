# API HTTP — SafeElo

Mesma origem da interface. JSON em UTF-8. Sessão via JSESSIONID HttpOnly. CSRF obrigatório em POST/PATCH/DELETE, inclusive login e cadastro. Obtenha `GET /api/csrf`, use o token no header retornado e renove após login/logout. Não adote autenticação por headers de plataformas externas.

| Método e rota | Permissão | Função |
|---|---|---|
| GET /api/health | Pública | Estado e configuração de e-mail, sem segredos |
| GET /api/csrf | Pública | Token CSRF da sessão/navegador |
| POST /api/auth/register | Pública + CSRF | name, email, password, role CITIZEN/SUPPORTER, phone opcional, consent=true |
| POST /api/auth/login | Pública + CSRF | Form URL-encoded: email e password; cria sessão própria |
| POST /api/auth/logout | CSRF | Invalida sessão e cookies |
| POST /api/auth/forgot | Pública + CSRF | Solicita recuperação por e-mail; indisponível sem SMTP |
| POST /api/auth/reset | Pública + CSRF | token e password; token de uso único |
| GET /api/me | Autenticada | Perfil e preferências |
| PATCH /api/me | Autenticada | name, phone e autoShareHigh; papel e e-mail não alteráveis |
| DELETE /api/me | Autenticada | Confirmação de password; exclui banco ativo por cascata |
| POST /api/analyses | Cidadão | kind TEXT/LINK/BOLETO/PIX, content até 8.000 caracteres |
| GET /api/analyses?page=0 | Proprietário | Histórico paginado, 12 por página, sem conteúdo integral |
| GET /api/analyses/{id} | Proprietário | Detalhe e conteúdo decifrado |
| DELETE /api/analyses/{id} | Proprietário | Remove verificação e alertas vinculados |
| POST /api/analyses/{id}/help | Proprietário | Compartilha com vínculos ativos, sem duplicar alertas |
| GET /api/elos | Autenticada | Vínculos próprios conforme papel |
| POST /api/elos/invite | Cidadão | email e relationship; retorna token uma vez |
| POST /api/elos/accept | Familiar | token privado do convite; requer e-mail indicado |
| POST /api/elos/{id}/approve | Cidadão proprietário | Confirma identidade do familiar |
| DELETE /api/elos/{id} | Participante | Revoga convite/vínculo |
| GET /api/support/alerts | Familiar | Apenas análises explicitamente compartilhadas em vínculos ativos |
| POST /api/support/alerts/{id}/acknowledge | Familiar vinculado | Marca acompanhamento |

Erros possuem `{"error":"mensagem"}` com status 400, 401, 403, 404, 409, 410, 413, 429 ou 503 conforme a situação. Erros internos não retornam stack trace. Recursos privados não pertencentes ao solicitante retornam 404. A classificação é LOW, CAUTION ou HIGH, acompanhada de sinais, recomendações e limitações. `emailConfigured` não significa entrega de um e-mail específico.
