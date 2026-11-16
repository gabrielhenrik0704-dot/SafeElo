# SafeElo — roteiro de fala

Apresentação editável com 13 slides. Duração sugerida: cerca de 7–9 minutos, ajustável ao tempo da disciplina. Os nomes dos demais integrantes podem ser adicionados à capa. As mesmas notas estão incorporadas no PowerPoint.

## Slide 01

Abertura — cerca de 25 segundos. Apresente o SafeElo como uma plataforma CitizenTech voltada à proteção digital e à inclusão. A proposta combina autonomia para entender uma mensagem com uma rede humana de apoio antes de uma decisão crítica. Apresentação: Gabriel Henrik Menezes Alves. Se houver outros integrantes, inclua os nomes na capa antes da entrega. Fonte: documentação inicial SafeElo fornecida pelo projeto.

## Slide 02

Problema — 35 segundos. Explique que o foco é o mecanismo de engenharia social, não culpar as vítimas. Pressa, medo e confiança são recursos que podem aparecer em uma fraude, mas sua presença isolada não prova golpe. O público prioritário vem da documentação. Não apresente estatísticas de prevalência: não foram coletadas neste projeto.

## Slide 03

Solução e ODS — 40 segundos. O primeiro pilar é a autonomia: uma explicação acessível ajuda o cidadão a pausar e conferir. O segundo é o Elo com uma pessoa de confiança. Relacione o ODS 10 à redução de barreiras de letramento e o ODS 16 à prevenção e ao acesso controlado às informações. São contribuições propostas, ainda sem estudo de impacto com usuários reais. Referências: documentação inicial e https://brasil.un.org/pt-br/sdgs/10 e https://brasil.un.org/pt-br/sdgs/16.

## Slide 04

Jornada — 35 segundos. Faça a sequência em uma frase e destaque que o sistema é preventivo. O cidadão seleciona o tipo de conteúdo, lê o resultado e pode solicitar ajuda. O histórico mantém a informação organizada. Caso o usuário habilite previamente o compartilhamento automático de alto risco, a análise é encaminhada aos vínculos ativos sem exigir o botão em cada ocorrência. Não há transferência de dinheiro no sistema.

## Slide 05

Interface — 35 segundos. A imagem é uma captura real do teste em celular, com dados fictícios. Foram conferidos desktop, largura de 320 pixels e ampliação de texto. Não afirme certificação WCAG ou validação com idosos: essas avaliações ainda não ocorreram. O objetivo de design é tornar a navegação compreensível e acessível, sem infantilizar o usuário.

## Slide 06

Motor — 45 segundos. O conteúdo é processado no backend Java, com regras persistidas na tabela RISK_RULE. O texto é normalizado; padrões recebem pesos e a soma é limitada a 100. O exemplo soma 25 por urgência, 20 por pagamento e 15 por possível familiar: 60. A classificação é LOW=0, CAUTION=1 a 59 e HIGH=60 a 100. Esses números são escolhas de regra, não probabilidades calibradas. Links não são abertos. Fonte: RiskEngine.java e RuleSeeds.

## Slide 07

Elo — 45 segundos. A confirmação humana é obrigatória antes de compartilhar. O convite tem token aleatório de uso único, hash no banco e validade de sete dias. O e-mail indicado restringe o aceite, mas não há verificação automatizada da propriedade desse e-mail; por isso, o cidadão deve conferir a identidade por outro canal. A revogação remove o acesso no app, sem apagar cópias externas já feitas pelo familiar. Fonte: EloService e testes de fluxo.

## Slide 08

Arquitetura — 40 segundos. O mesmo processo Spring Boot serve a interface e a API. O acesso usa conta e sessão próprias, sem dependência de ChatGPT ou serviços de IA. H2 em arquivo atende à execução local acadêmica; PostgreSQL foi testado de verdade. Flyway cria e versiona o schema. O pacote possui o JAR já compilado. A imagem de runtime Docker foi construída e executada; o build multi-stage completo não foi validado dentro do container, conforme docs/VALIDACAO.md.

## Slide 09

Banco — 45 segundos. Explique as relações principais, sem ler todos os campos. APP_USER reúne cidadão e familiar com papéis distintos. ELO_LINK registra a relação e o estado do consentimento. SUPPORT_ALERT liga uma análise a um vínculo; há unicidade por par para evitar duplicação do pedido. Exclusões usam integridade referencial e cascata. A prévia da documentação foi detalhada em seis tabelas. Fonte: V1__initial_schema.sql e Model.java.

## Slide 10

Segurança — 45 segundos. Não apresente proteção absoluta ou certificação de LGPD. Senhas são hashes BCrypt com custo 12. AES-GCM protege conteúdo e telefone no armazenamento; o servidor possui a chave, portanto esta arquitetura não é E2EE. Nome, e-mail e metadados não são cifrados. Consultas são parametrizadas por JPA, com validação e tratamento seguro da saída. CSP restringe scripts e o app não abre URLs enviadas. Produção exige TLS, revisão independente, política de retenção, controlador e contato institucional.

## Slide 11

Validação — 45 segundos. Os cinco testes de integração contêm vários cenários, não apenas cinco cliques. A suíte passou sem falhas. Playwright e Chromium verificaram os fluxos reais com contas de teste separadas. SMTP foi testado por um capturador local Mailpit; isso não comprova entrega por provedor externo. Não houve teste com idosos, auditoria formal, estudo de impacto, teste de carga ou conformidade WCAG/LGPD certificada. Fonte: relatório Surefire e docs/VALIDACAO.md.

## Slide 12

Escopo — 35 segundos. A entrega acadêmica funciona localmente; não exige contratar hospedagem. E-mail externo é opcional: sem SMTP, o pedido existe no painel e a recuperação por e-mail fica indisponível. Reputação ao vivo, OCR, anexos, consulta ao Banco Central, validação de PIX copia-e-cola e verificação completa de boleto de 48 dígitos não foram implementadas. As próximas etapas precisam responder a necessidades reais e aos testes com o público, sem transformar hipóteses em promessas.

## Slide 13

Encerramento — 20 segundos. Retome a proposta: orientar antes da decisão e facilitar um apoio consentido. Convide a banca a consultar o repositório público, as capturas reais e a documentação. Para usar o sistema completo, é preciso executar o Java localmente e abrir localhost:8080 no mesmo computador. O GitHub exibe documentação e arquivos, não executa o backend. Se houver tempo, demonstre uma verificação e um pedido no Elo. Não há senha padrão nem conta de demonstração incluída.

## Referências de conteúdo

- Documentação Inicial do Projeto SafeElo, fornecida para esta atividade.
- Código e documentação do repositório: https://github.com/gabrielhenrik0704-dot/SafeElo
- docs/ARQUITETURA.md e docs/VALIDACAO.md.
- ONU Brasil — ODS 10: https://brasil.un.org/pt-br/sdgs/10
- ONU Brasil — ODS 16: https://brasil.un.org/pt-br/sdgs/16
