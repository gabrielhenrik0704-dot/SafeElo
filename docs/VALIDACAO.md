# Registro de validação — 16/11/2026

Os testes usaram dados sintéticos, sem participantes reais. Mensagens de exercício foram realmente processadas pelo Java e registradas no banco; não são respostas demonstrativas.

## Backend

Suíte Spring Boot + MockMvc em H2 em memória:

- Cadastro e login, rejeição de senha incorreta, CSRF e validação de entrada.
- Conteúdo cifrado e hash de senha no banco; IV aleatório e associação criptográfica ao contexto.
- Convite, aceite pelo e-mail indicado, confirmação pelo cidadão e impedimento de compartilhamento antes do vínculo ativo.
- Pedido manual, prevenção de alerta duplicado, acompanhamento, isolamento de outro familiar e acesso ao histórico apenas pelo proprietário.
- Compartilhamento automático somente com preferência habilitada e vínculo ativo.
- Revogação, exclusão de verificação e exclusão de conta.
- Recuperação por token de uso único e expiração das sessões antigas.
- Regras de pontuação, URL com destino oculto, boleto e formato de PIX.

## Navegador real

Playwright + Chromium, sem enfraquecer a CSP do aplicativo:

- Cadastro, login e ciclo de Elo com duas contas separadas.
- Análise de alto risco, compartilhamento e acompanhamento.
- Conta externa não vê os dados; revogação remove acesso.
- Histórico após logout/login, conteúdo HTML tratado como texto.
- Layout desktop e celular, incluindo 320 px e texto ampliado, sem rolagem horizontal no fluxo testado.

O fluxo completo foi executado com H2 em arquivo e repetido com PostgreSQL 17 real em container. O schema foi criado pelo Flyway no PostgreSQL e validado pelo Hibernate.

## SMTP

Um servidor SMTP de captura local (Mailpit) recebeu mensagens reais do Java. Nenhuma mensagem foi enviada à internet. O teste confirmou:

- Aviso de pedido de ajuda sem incluir o conteúdo analisado.
- Link de recuperação recebido por SMTP.
- Redefinição na interface enquanto a conta estava conectada.
- Novo login, rejeição do token reutilizado e exclusão da conta de teste.

Isso valida o transporte local e o código. Não comprova entrega por um provedor externo ou à caixa de entrada em produção. O responsável pela implantação deve configurar e testar o serviço real.

## O que não foi certificado

Não foi realizada auditoria independente, avaliação com idosos/usuários reais, teste formal de conformidade WCAG/LGPD, teste de carga ou segurança abrangente, nem operação em domínio público. Configuração de produção, revisão de dependências, monitoramento e políticas institucionais precisam ser concluídos antes de uso com dados sensíveis reais.

## Execução em container

A imagem de runtime foi construída a partir do JAR compilado e executada como usuário sem privilégios, raiz somente leitura, capacidades removidas e PostgreSQL em rede Docker privada. O fluxo de navegador completo foi repetido nessa instalação e passou. O build multi-stage usa as mesmas fontes e testes, mas não foi exercitado integralmente dentro do container; o JAR foi compilado e testado com JDK 17 antes do build de runtime.
