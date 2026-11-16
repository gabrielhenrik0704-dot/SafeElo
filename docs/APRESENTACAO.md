# Apresentação acadêmica — SafeElo

## Problema e público

Fraudes digitais e engenharia social afetam especialmente pessoas que ainda não dominam os sinais de risco. SafeElo combina autonomia e uma rede de apoio consentida, com linguagem acessível e sem prometer detecção perfeita.

## Demonstração do sistema funcional

1. Mostrar a página inicial e criar uma conta de cidadão e uma de familiar.
2. Verificar uma mensagem de exercício identificada como fictícia. Explicar quais regras contribuíram para a pontuação; mostrar que o resultado foi calculado pelo Java, não pré-gravado.
3. Mostrar o histórico persistente.
4. Criar o Elo, aceitar na outra conta e confirmar pelo cidadão. Explicar que papel autodeclarado e e-mail não comprovam identidade.
5. Compartilhar uma análise e acompanhar o pedido no painel do familiar.
6. Revogar o vínculo e comprovar que a verificação deixa de estar disponível para o familiar.
7. Mostrar tabela de dados, migração SQL, hash BCrypt e conteúdo cifrado, sem exibir chaves ou senhas.
8. Explicar limitações e o que depende da instalação: SMTP, domínio, TLS, retenção e operação.

## ODS

- ODS 10: acessibilidade, menor barreira de letramento digital e apoio para decisões.
- ODS 16: prevenção, orientação clara, privacidade e acesso controlado às informações. O produto não substitui instituições ou atribui crime a pessoas.

## Pitch técnico

Java 17/Spring Boot, JPA/Hibernate, Flyway, H2/PostgreSQL, motor de regras persistidas, sessões/CSRF/BCrypt e AES-GCM em campos sensíveis. O monólito serve frontend e API na mesma origem. Não depende de login de ChatGPT, serviços de IA ou protótipos desconectados.

## Limites que devem ser ditos

Não é antivírus, verificador de identidade, detector infalível de golpes ou validador de beneficiário financeiro. O alerta é heurístico. A redução de danos depende também de educação, confirmação por outro canal e decisão humana.
