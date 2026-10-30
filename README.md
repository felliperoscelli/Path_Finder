# Path Finder

Aplicativo Android para criação de pistas de orientação (bússola), acompanhamento
dos monitores em tempo real e comunicação entre aparelhos sem internet.
A especificação completa está em [`docs/especificacao.md`](docs/especificacao.md).

## Status das etapas

| Etapa | Conteúdo | Status |
|---|---|---|
| 1 | Estrutura do projeto, banco local e mapa offline com OpenTopoMap | ✅ |
| 2 | Gravação de trilha, pontos, azimute, declinação e UTM | — |
| 3 | Edição de pontos e ficha em PDF | — |
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
├── map/                                 # OpenTopoMap, cálculo de tiles e download offline
└── ui/                                  # telas Compose (home, map, offline, settings)
```
