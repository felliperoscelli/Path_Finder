# Path Finder

Aplicativo Android para criação de pistas de orientação (bússola), acompanhamento
dos monitores em tempo real e comunicação entre aparelhos sem internet.
A especificação completa está em [`docs/especificacao.md`](docs/especificacao.md).

## Status das etapas

| Etapa | Conteúdo | Status |
|---|---|---|
| 1 | Estrutura do projeto, banco local e mapa offline com OpenTopoMap | ✅ |
| 2 | Gravação de trilha, pontos, azimute, declinação e UTM | ✅ |
| 3 | Edição de pontos e ficha em PDF | ✅ |
| 4 | Navegação do monitor e alerta de desvio local | ✅ |
| 5 | Nearby Connections entre dois aparelhos | ✅ |
| 6 | Rede em malha com repasse e armazenamento | ✅ |
| 7 | Painel do coordenador, alertas remotos e mensagens | ✅ |
| 8 | Testes de campo e ajustes de bateria | ✅ (ferramentas prontas; teste em campo pendente) |

## Etapa 1 – o que já funciona

- **Projeto**: Kotlin + Jetpack Compose (Material 3), minSdk 26, targetSdk 35.
- **Banco local (Room)** – `data/db`:
  - `trails`: trilha com limites de desvio próprios (padrão 30 m / 5 min) e declinação usada;
  - `trail_points`: pontos com lat/lon, UTM (zona, banda, E, N), azimute verdadeiro e
    magnético, distância ao anterior, nome, observações e foto;
  - `track_points`: trajeto gravado pelo GPS (criação da trilha ou percurso real do grupo);
  - `offline_areas`: áreas de mapa baixadas.
  - O esquema é exportado em `app/schemas/` para futuras migrações.
- **Configurações (DataStore)** – seção 10: nome, perfil, distância/tempo do desvio,
  tempo de parada, intervalo de posição e formato de coordenadas.
- **Mapa topográfico** – osmdroid com camada OpenTopoMap (curvas de nível), posição
  atual do usuário, escala e exibição da trilha (linha e pontos numerados).
- **Aparência do mapa** – botões redondos flutuantes à direita: camadas, bússola (a agulha
  vermelha mostra o norte; tocar volta o mapa para norte acima) e minha localização. O mapa
  gira com dois dedos. Ponto azul com seta na direção do movimento e pinos numerados (o
  próximo ponto da navegação fica verde). Bandeira verde no início da pista e quadriculada no
  fim (escondidas no modo desafio, como os pontos).
- **Zoom acima do 17** – as curvas de nível e o satélite só têm imagem até o zoom 17 (escala
  de ~50 m). Mais perto, o tile do 17 é ampliado com suavização (`map/SmoothTileProvider.kt`)
  em vez dos "quadradões" do osmdroid; os tiles do 17 que faltam na área são baixados. Não
  ganha detalhe: só fica liso.
- **Mapa em tempo real** (botão Mapa do início) – abre na posição do usuário e o acompanha até
  ele arrastar o mapa. O botão "Trilhas" escolhe quais trilhas aparecem, **várias ao mesmo
  tempo**, cada uma com uma cor (`map/TrailColors.kt`) e legenda no topo; a escolha fica
  salva. Tocar num ponto mostra a trilha e o ponto, com "Abrir trilha" e "Navegar".
  "Gerenciar" leva à lista de trilhas (criar, importar, apagar).
- **Camadas** – botão de camadas: **Curvas de nível** (OpenTopoMap) ou **Satélite**
  (EOxCloudless/Sentinel-2 2024, ~10 m por pixel, uso não comercial com atribuição). A escolha
  vale para todos os mapas. O satélite não é baixado em "Mapas offline" (os termos do serviço
  gratuito não preveem download de áreas): sem internet ele mostra só o que já foi visto com
  internet, guardado por 1 ano.
- **Mapa offline** – tela "Mapas offline": posicione o mapa sobre a área do evento,
  escolha os níveis de zoom (até 17), veja a estimativa de tiles/MB e baixe. As áreas
  ficam salvas no aparelho (cache de até 2 GB, válido por 1 ano) e funcionam sem
  internet. Cada área tem limite de 20 000 tiles para respeitar o servidor do OpenTopoMap.
- **Telas**: abertura com a logo, página inicial (Mapa, Grupo, Configurações, Mensagens,
  cartão "Em andamento" e tutorial "Como usar o app"), mapa em tempo real, lista de trilhas
  (criar/apagar), mapa da trilha, mapas offline e configurações.

## Etapa 2 – gravação da trilha

- **Gravação pelo GPS**: no mapa da trilha, "Gravar" inicia um serviço em primeiro plano
  (Fused Location Provider, leitura a cada 2 s) que continua com a tela desligada. O
  percurso é salvo em `track_points` (uma amostra a cada 5 m ou 30 s, descartando
  leituras com imprecisão acima de 50 m) e aparece em azul no mapa.
- **Detecção de parada**: parado pelo tempo configurado (padrão 25 s) dentro de ~10 m,
  o app pergunta "Marcar ponto aqui?" na tela e por notificação com vibração. O ponto
  sugerido é a média das leituras enquanto parado, mais precisa que uma leitura isolada.
- **Pontos manuais**: "Marcar ponto" usa a posição atual do GPS; tocar e segurar no mapa
  marca um ponto em qualquer lugar (útil para planejar a pista em casa).
- **Cada ponto guarda**: lat/lon, UTM (zona, banda, E, N), azimute verdadeiro e magnético
  e distância desde o ponto anterior, nome, observações e foto opcional (câmera do aparelho).
  O diálogo mostra esses valores antes de salvar.
- **Cálculos** (`geo/`, com testes unitários contra pyproj/GeographicLib):
  - distância e azimute verdadeiro pelas fórmulas de Vincenty no elipsoide WGS84;
  - UTM pela série de Krüger (precisão submilimétrica), com exceções da Noruega/Svalbard;
  - declinação pelo `GeomagneticField` do Android, calculada no primeiro ponto e fixada
    na trilha para que todos os azimutes (e a futura ficha em PDF) usem o mesmo valor;
  - azimute magnético = verdadeiro − declinação (declinação oeste é negativa, então em
    Caldas Novas o magnético fica cerca de 21° maior que o verdadeiro).
- **Lista de pontos**: botão de lista no mapa mostra todos os pontos no formato de
  coordenada escolhido nas configurações, com azimutes, distâncias, declinação e fotos.

## Etapa 3 – edição de pontos e ficha em PDF

- **Edição no mapa**: o lápis na barra superior ativa o modo edição; toque e segure um
  marcador e arraste para movê-lo (a linha acompanha o arraste). Tocar num ponto abre
  o formulário para renomear, mudar observações, trocar/remover a foto ou apagar.
- **Inserir pontos**: ao criar um ponto (GPS ou toque longo no mapa) é possível escolher
  a posição na trilha. O app sugere o trecho mais próximo de onde o ponto caiu, ou o
  final se ele estiver além do último ponto.
- **Reordenar**: na lista de pontos, setas para subir/descer cada ponto. Ao editar um ponto,
  "Número na pista" leva o ponto direto para outra posição (o 3 virando 2 faz o antigo 2 virar
  3), mostrando a nova ordem antes de salvar.
- **Recálculo automático**: mover, apagar, inserir ou reordenar recalcula UTM,
  azimutes e distâncias da trilha inteira. A declinação fica a mesma da trilha.
- **Editar trilha**: nome e limites de desvio próprios (distância e tempo).
- **Ficha em PDF** (botão PDF na lista de pontos), A4 com cabeçalho repetido em cada página:
  - *Completa*: coordenadas, azimute verdadeiro, azimute magnético, distância e observações;
  - *Só coordenadas*: colunas de azimute e distância em branco, com espaço para o grupo
    preencher à mão;
  - coordenadas em UTM, lat/long ou ambos; observações opcionais;
  - cabeçalho com nome da pista, data, declinação usada e formato das coordenadas;
  - depois de gerada: imprimir (serviço de impressão do Android), compartilhar
    (WhatsApp, e-mail…) ou salvar em uma pasta.

## Etapa 4 – navegação do monitor e alerta de desvio

- **Iniciar**: ícone de bússola na lista de trilhas (ou no mapa da trilha). Com o perfil
  *Monitor*, tocar na trilha já abre a navegação. O monitor informa grupo e nome.
- **Tela de navegação**: mapa offline com a trilha, posição atual, linha tracejada até o
  próximo ponto (verde) e pontos alcançados esmaecidos. Cartão com o próximo ponto,
  distância, azimute magnético e verdadeiro e uma seta que aponta para o ponto usando a
  bússola do celular.
- **Chegada**: o ponto é alcançado ao entrar no raio de chegada (padrão 15 m, nas
  configurações), com som curto e vibração. Chegar a um ponto à frente pula os anteriores;
  há botões para pular ou voltar um ponto.
- **Alerta de desvio**: dispara quando o grupo fica mais longe da rota que o limite da
  trilha (padrão 30 m) por todo o tempo configurado (padrão 5 min). Toca o som de alarme
  e vibra, mesmo com a tela desligada, e repete a cada minuto até voltar à rota ou ser
  silenciado. Uma faixa colorida mostra: na rota (verde), afastado com cronômetro
  (laranja) ou fora da rota (vermelho).
  - A rota é a linha reta entre os pontos **ou** o percurso gravado pelo coordenador
    (vale a menor distância), para não acusar desvio quando o grupo contorna um
    obstáculo pelo mesmo caminho.
  - O alerta só começa depois de chegar ao ponto de partida.
  - Leituras de GPS com imprecisão acima de 50 m são ignoradas.
- **Trajeto real**: gravado em segundo plano e salvo por percurso. Em "Percursos
  anteriores" é possível rever no mapa o caminho do grupo (laranja), os pontos alcançados,
  onde cada alerta de desvio aconteceu, a duração e a distância percorrida.
- **Banco de dados v2** com migração a partir da v1 (sessões de navegação, eventos de
  desvio e `sessionId` no trajeto), testada contra o esquema exportado.

## Etapa 5 – comunicação entre aparelhos e compartilhamento da trilha

- **Rede local** (Início → Grupo → Rede local): liga a Nearby Connections (estratégia
  `P2P_CLUSTER`, Bluetooth + Wi-Fi Direct) em um serviço em primeiro plano. Cada aparelho
  anuncia e procura ao mesmo tempo e conecta sozinho aos outros aparelhos com o app e a
  rede ligada; reconecta se a ligação cair. A tela mostra os aparelhos e o estado de cada um.
  O nome mostrado é o das Configurações.
- **Protocolo** (`net/Protocol.kt`): toda mensagem tem ID único, origem, horário, contador
  de saltos e prioridade (alerta > normal > posição), em JSON compactado com GZIP. Cópias
  repetidas são descartadas pelo ID. Mensagens grandes (trilhas com percurso longo) vão
  como stream.
- **Coordenador → monitor**: na lista de pontos, "Enviar trilha" → "Enviar para N
  aparelhos". O monitor recebe uma notificação e a trilha aparece na lista; um reenvio da
  mesma trilha (mesmo identificador) atualiza a cópia em vez de duplicar.
- **Monitor → coordenador**: durante a navegação o monitor envia a posição e o progresso
  no intervalo das configurações (padrão 30 s), ao alcançar cada ponto, e na hora em que o
  alarme de desvio dispara ou o grupo volta à rota.
- **No coordenador**: a tela "Rede local" lista os grupos com trilha, próximo ponto,
  último ponto alcançado, distância da rota e tempo desde a última atualização. Um alerta
  de desvio toca o alarme, vibra e mostra notificação; há botão para silenciar.
  (O painel com mapa vem na etapa 7.)
- **Arquivos**: exportar a trilha como `.json` (formato completo do Path Finder, inclui o
  percurso gravado) ou `.gpx` (abre em Google Earth, OsmAnd etc.) para compartilhar ou
  salvar; importar `.json` ou `.gpx` pelo menu da tela Trilhas.
- **Banco de dados v3**: cada trilha ganhou um identificador global (`uuid`), com migração.

Observação: a rede aceita qualquer aparelho com o Path Finder e a rede ligada por perto.

## Etapa 6 – rede em malha com repasse e armazenamento

- **Repasse** (`net/MeshCore.kt`): cada aparelho entrega a si mesmo e repassa às outras
  conexões toda mensagem nova, com o contador de saltos +1 (até 16 saltos, suficiente para
  pistas de ~2 km com aparelhos a cada 150 m). Cópias com o mesmo ID são descartadas, o que
  evita duplicação e loops.
- **Armazenar e encaminhar**: as mensagens ficam guardadas no banco (sobrevivem ao
  fechamento do app) até vencer. Quando um aparelho se conecta, recebe tudo o que ainda não
  tem. Cada mensagem registra quem já a recebeu, para não reenviar.
- **Validade**: posição 15 min, ponto alcançado 2 h, alertas 6 h, trilha 24 h. Só a
  posição mais recente de cada monitor é guardada e repassada; o mesmo vale para a versão
  mais nova de cada trilha e para as repetições de um mesmo alerta. O armazenamento é
  limitado a 2 000 mensagens, descartando primeiro as menos importantes.
- **Prioridade**: cada conexão tem sua fila de envio; alertas saem antes de mensagens
  normais, e estas antes das posições. O próximo envio só começa quando o anterior é
  confirmado.
- **Mensagens diretas**: o protocolo aceita destinatário (`to`); os outros aparelhos só
  repassam. Será usado nas mensagens individuais da etapa 7.
- **Perfil Repetidor** (Configurações): para celulares antigos fixos nos pontos
  intermediários, ligados a power bank. A tela Grupo vira um painel com botão para ligar
  a rede e os números de mensagens guardadas e repassadas; o GPS não é usado.
- A tela "Rede local" mostra os números da malha (guardadas, recebidas, repassadas, enviadas).
- **Banco de dados v4** com a tabela das mensagens da malha, com migração.

## Etapa 7 – painel do coordenador, alertas remotos e mensagens

- **Painel** (perfil Coordenador, Início → Grupo → Painel do coordenador): mapa topográfico offline com a
  trilha (escolhida automaticamente pela que os grupos estão navegando, ou pelo menu) e
  um ícone por grupo com o nome, na cor do estado: verde na rota, laranja afastado,
  vermelho fora da rota, cinza sem notícias há mais de 3 min. Tocar no ícone mostra o
  monitor, o último ponto alcançado e há quanto tempo veio a última atualização. Embaixo,
  a lista de grupos com progresso; tocar centraliza o mapa.
- **Alertas no topo do painel**, com som e vibração: emergências e grupos fora da rota,
  com grupo, última posição e tempo fora da rota. Botões: ver no mapa, **responder**
  (respostas rápidas como "Recebido! Aguardem, resgate a caminho" ou texto livre, enviadas
  só para aquele monitor), silenciar o desvio e "Ciente" na emergência.
- **Indicador de rede** no painel: "Malha · N" com o número de aparelhos conectados.
- **Mensagens** (todos os perfis, menos Repetidor): conversa com mensagens gerais ou
  individuais (escolha do destinatário entre os aparelhos conhecidos, ou "Responder só
  para…"). Histórico guardado no aparelho; contador de não lidas no botão Mensagens do início e na
  navegação. Mensagens recebidas geram notificação.
- **Alertas rápidos** do monitor: "Grupo perdido" e "Precisamos de ajuda" (urgentes:
  prioridade de alerta e alarme no coordenador), "Chegamos ao ponto" e "Tudo certo",
  todos com a posição. O coordenador tem respostas rápidas próprias.
- **Botão de emergência** (na tela de mensagens e, durante a navegação, no canto da tela):
  pede confirmação e envia um alerta prioritário com a posição. No coordenador o alarme
  repete a cada 30 s até tocar em "Ciente". Sem conexão, a emergência fica guardada e
  sai assim que houver um aparelho por perto.
- A última mensagem não lida aparece também na tela de navegação do monitor.
- **Banco de dados v5** com o histórico de mensagens, com migração.

## Etapa 8 – testes de campo e ajustes de bateria

- **Economia de bateria** (Configurações → Bateria e testes: Automática, Sempre ou Desligada),
  regras em `power/PowerPolicy.kt`:
  - parado há 2 min: GPS a cada 10 s em vez de 2 s (volta ao normal ao andar);
  - bateria ≤ 30% (ou "Sempre"): GPS a cada 5 s e posição com o dobro do intervalo (mín. 60 s);
  - bateria ≤ 10%: GPS a cada 10 s e posição com o triplo (mín. 120 s);
  - carregando ou "Desligada": sempre no máximo.
  A tela de navegação mostra "economia de bateria" quando ativa.
- **Diagnóstico de campo** (Configurações → Diagnóstico de campo): consumo de bateria em %/hora
  e estimativa de duração, qualidade do GPS (precisão mediana e 90%), conexões e quedas da
  rede, mensagens da malha e alertas. Tudo fica num registro `.csv` (abre em planilha) para
  exportar e enviar depois do teste.
- **Ensaio com caminhada simulada** (ligar em Configurações): na tela de navegação, "Ensaiar"
  percorre a trilha sem sair do lugar, a 1x, 5x ou 20x, com opção de forçar um desvio. Serve
  para testar alarmes, rede, painel, mensagens e resultado com os celulares na mesa. O grupo
  aparece como `[SIM]`.
- **Roteiro do teste de campo**: [`docs/teste-de-campo.md`](docs/teste-de-campo.md).

## Recursos extras (segurança, criação de pistas e campo)

1. **Código do evento** (Configurações → Evento): só celulares com o mesmo código formam a
   rede. O código entra no identificador da Nearby (eventos diferentes nem se enxergam) e em
   cada mensagem (mensagens de outro evento não são entregues nem repassadas). Aplicar um
   código novo religa a rede.
   **PIN do coordenador**: com um PIN definido, trocar o perfil para Coordenador pede o PIN
   (guardado só como hash); trocar ou remover o PIN pede o atual.
2. **Bateria e "sem notícias"**: cada posição leva a bateria do celular do monitor, mostrada
   no painel e na lista de grupos. O coordenador recebe alarme quando um grupo fica sem
   notícias pelo tempo configurado (padrão 10 min; contado pelo relógio do coordenador, para
   não depender do relógio do monitor) e aviso de bateria baixa (≤ 15%). O monitor vê um
   aviso com bateria ≤ 20%. Ao encerrar, o monitor avisa o coordenador, e o grupo sai da vigia.
3. **Hora limite** (editar trilha): os grupos recebem aviso 30 min antes e alarme na hora,
   com botão para voltar à base; o coordenador recebe alarme para cada grupo que não terminou.
4. **Coordenadas digitadas**: botão de teclado no mapa da trilha cria um ponto digitando
   lat/long (decimal, graus-minutos-segundos, N/S/L/O) ou UTM (zona, hemisfério, E, N), com a
   conversão mostrada antes de confirmar. No ponto, "Corrigir coordenadas" muda a posição.
   **Declinação manual** (editar trilha): usa o valor da carta e recalcula os azimutes.
5. **Modo desafio** (editar trilha): o monitor não vê distância, direção, seta, linha da
   trilha nem os pontos ainda não alcançados; o app só avisa a chegada e o desvio.
6. **Resultado** (troféu na lista de pontos ou "Resultado" na navegação): classificação pelo
   tempo total (do ponto 1 ao último), tempos por trecho, alertas e tempo fora da rota. Os
   monitores enviam o resumo ao encerrar, então o coordenador vê todos os grupos. Relatório
   em PDF para imprimir ou compartilhar.
7. **Tela acesa** durante a navegação (configurável) e **alto contraste** com letras maiores
   para ler no sol (Configurações → Tela).
8. **Voltar à base**: botão "Base" na navegação, pela hora limite ou a pedido do coordenador
   (no painel: "Responder" → "Mandar voltar à base", ou para todos no menu de trilhas). O app
   guia até a base (definida no ponto com "Usar como base", ou a partida), com distância,
   azimute e seta mesmo no modo desafio, e pausa o alerta de desvio. Dá para retomar a trilha.

**Banco de dados v6**, com migração verificada a partir de todas as versões anteriores.

## Como compilar

Requisitos: JDK 17+ e Android SDK (platform 35). Abra a pasta no Android Studio ou:

```bash
./gradlew assembleDebug        # gera app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # testes unitários
```

## Estrutura

```
app/src/main/java/com/pathfinder/
├── PathFinderApp.kt / AppContainer.kt   # inicialização e dependências
├── data/                                # Room, repositórios e configurações
├── diagnostics/                         # registro e resumo do diagnóstico de campo
├── geo/                                 # UTM, Vincenty, declinação, detecção de parada
├── navigation/                          # regras da navegação, alarme e serviço do monitor
├── location/                            # posição atual para mensagens e emergência
├── map/                                 # OpenTopoMap, cálculo de tiles e download offline
├── pdf/                                 # conteúdo e desenho da ficha, imprimir/compartilhar
├── net/                                 # Nearby Connections, protocolo, painel de monitores
├── results/                             # resultado: classificação, tempos por trecho e PDF
├── power/                               # regras de economia de bateria
├── recording/                           # serviço de gravação pelo GPS e notificações
├── transfer/                            # trilha em JSON/GPX, exportar e importar
└── ui/                                  # telas Compose (home, map, points, navigate, dashboard, messages, results, network, diagnostics, offline, settings)
```
