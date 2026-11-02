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
| 4 | Navegação do monitor e alerta de desvio local | — |
| 5 | Nearby Connections entre dois aparelhos | — |
| 6 | Rede em malha com repasse e armazenamento | — |
| 7 | Painel do coordenador, alertas remotos e mensagens | — |
| 8 | Testes de campo e ajustes de bateria | — |

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
- **Mapa offline** – tela "Mapas offline": posicione o mapa sobre a área do evento,
  escolha os níveis de zoom (até 17), veja a estimativa de tiles/MB e baixe. As áreas
  ficam salvas no aparelho (cache de até 2 GB, válido por 1 ano) e funcionam sem
  internet. Cada área tem limite de 20 000 tiles para respeitar o servidor do OpenTopoMap.
- **Telas**: lista de trilhas (criar/apagar), mapa, mapas offline e configurações.

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
- **Reordenar**: na lista de pontos, setas para subir/descer cada ponto.
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
├── geo/                                 # UTM, Vincenty, declinação, detecção de parada
├── map/                                 # OpenTopoMap, cálculo de tiles e download offline
├── pdf/                                 # conteúdo e desenho da ficha, imprimir/compartilhar
├── recording/                           # serviço de gravação pelo GPS e notificações
└── ui/                                  # telas Compose (home, map, points, offline, settings)
```
