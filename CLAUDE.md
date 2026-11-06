# Path Finder – guia para o Claude Code

App Android de pistas de orientação (bússola) para clubes: o coordenador cria a pista, os
monitores navegam com alerta de desvio, tudo funciona **sem internet** (rede em malha entre
celulares). Especificação: `docs/especificacao.md`. Estado e funcionalidades: `README.md`.
Roteiro do teste de campo: `docs/teste-de-campo.md`.

Idioma: interface, comentários, commits e conversa em **português do Brasil**.

## Comandos

```bash
./gradlew assembleDebug        # APK em app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # testes JVM (rápidos, sem emulador)
./gradlew lintDebug            # lint; manter sem avisos novos
```

Sempre rodar testes + lint antes de commitar. A lógica fica em código puro e testável
(sem Android); a UI e os serviços chamam essa lógica.

## Arquitetura (`app/src/main/java/com/pathfinder/`)

- Kotlin + Jetpack Compose (Material 3), minSdk 26, targetSdk 35. Injeção manual em
  `AppContainer.kt` (uma instância de cada serviço; ViewModels via `appContainer()`).
- `data/` Room (`db/`), repositórios, `settings/` (DataStore, `AppSettings`).
- `geo/` cálculos puros: Vincenty, UTM (Krüger direta e inversa), declinação
  (`GeomagneticField`), leitura de coordenadas digitadas, hora do dia.
- `map/` osmdroid + OpenTopoMap e download offline (`OfflineMapManager`, `TileMath`).
- `recording/` serviço de gravação da trilha (GPS) e `Notifications` (todos os canais).
- `navigation/` motor da navegação (`NavigationEngine`, puro), `NavigationService`
  (primeiro plano), alarme (`AlertPlayer`), hora limite, caminhada simulada.
- `net/` rede: `NearbyManager` (transporte Nearby P2P_CLUSTER), `MeshCore` (malha: repasse,
  saltos, armazenar e encaminhar, filas por prioridade), `Protocol.kt` (mensagens),
  `MeshPolicy`, `MonitorBoard` (painel do coordenador, puro), `NetworkHub` (liga tudo ao app),
  `EventCode`.
- `transfer/` trilha em JSON/GPX. `pdf/` ficha e relatórios. `results/` resultado da pista.
- `power/` economia de bateria. `diagnostics/` registro CSV de campo.
- `ui/` telas Compose por pasta (splash, home = página inicial, livemap = mapa em tempo real,
  trails, group, tutorial, map, points, navigate, dashboard, messages, results, network,
  diagnostics, offline, settings) e `components/` compartilhados. `plans/` planos
  (Grátis / Modo grupo).
- `map/SmoothTileProvider.kt`: provedor de tiles do osmdroid que amplia com filtro acima do
  zoom 17 (pula o `rescaleCache` do osmdroid lá em cima e baixa os tiles do 17 que faltam).

## Regras importantes

- **Banco de dados**: ao mudar entidades, subir `version` em `AppDatabase`, adicionar a
  migração em `data/db/Migrations.kt` (lista `SQL_x_y`) e conferir contra o esquema exportado
  em `app/schemas/` (há testes em `MigrationSqlTest`). Colunas NOT NULL novas precisam de
  `@ColumnInfo(defaultValue = ...)` igual ao `DEFAULT` da migração. Nunca usar migração
  destrutiva: os celulares já têm dados.
- **Protocolo da rede**: mensagens novas são `Body` em `net/Protocol.kt`; definir validade e
  substituição em `MeshPolicy`. Campos novos com valor padrão (compatível com versões antigas).
- **Mapas**: o app precisa funcionar offline. Não usar tiles do Google (termos proíbem uso
  offline). Antes de adicionar um fornecedor de tiles, confirmar que os termos permitem
  download para uso offline e respeitar o limite por área (`OfflineMapManager`).
- Testes de coroutines da malha: usar o padrão `meshTest` de `MeshCoreTest` (escopo próprio
  no relógio do teste; `advanceUntilIdle` não roda o `backgroundScope`).

## Estado atual (nov/2026)

Etapas 1–8 da especificação implementadas, mais os recursos extras (código do evento, PIN,
vigia de grupos, hora limite, coordenadas digitadas, modo desafio, resultado, alto contraste,
voltar à base). O app já foi instalado e funciona no celular; teste de campo completo pendente.

## Decisões de produto

### Monetização (ainda não implementada)

- **Grátis – navegação simples:** criar e editar trilhas, navegar sozinho, mapas offline,
  ficha em PDF, GPX/JSON, voltar à base, hora limite e modo desafio no próprio celular.
- **Modo grupo – pago, compra única por usuário** (conta Google; quem compra é o usuário,
  não o clube). Libera todas as formas de comunicação (Bluetooth/Wi-Fi Direct via Nearby,
  LoRa e internet no futuro), painel do coordenador, mensagens, alertas remotos,
  **emergência** e resultado. A emergência faz parte do pago: sem grupo não há quem receba.
- **Assinatura** só no futuro, para o que tiver custo mensal (modo online/Firebase, mapa
  satélite comercial).
- **Teste grátis** do Modo grupo controlado pelo app (7 dias ou primeiro evento: a decidir).
  A Play não oferece teste grátis em compra única.
- Plano de implementação: camada de planos (Grátis / Modo grupo) com a licença guardada no
  aparelho para funcionar offline, **tudo liberado durante o piloto**; depois Google Play
  Billing (exige o app no teste interno da Play) e códigos promocionais da Play.
- Antes de publicar: verificar se o nome "Path Finder" está livre (INPI e Play Store).

### LoRa (atualização futura, ainda não implementar)

- O celular não tem rádio LoRa: precisa de um rádio externo ligado por Bluetooth LE.
  Candidato: placas compatíveis com Meshtastic (Heltec, LilyGO T-Beam, RAK WisBlock).
- Entra como mais um `MeshTransport` na `MeshCore`; descarte de cópias, prioridade e
  armazenar e encaminhar continuam valendo.
- O LoRa carrega pouquíssimos dados: por ele só emergência, alertas, posições e mensagens
  curtas, em formato binário compacto (posição ≈ 20 bytes). Trilha completa e resultado
  continuam por Bluetooth/Wi-Fi.
- Antes de implementar: definir o hardware, quem leva rádio (todos ou só alguns, como
  ponte), conferir a licença GPL do Meshtastic para uso em app pago e fechado, e a
  homologação Anatel (915 MHz).

## Próximos passos combinados

1. **Aparência do mapa – feito (nov/2026)**: botões redondos (`ui/components/MapControls.kt`:
   camadas, bússola, localização), giro do mapa, ponto azul com seta e pinos numerados
   (`MapIcons.kt`), camadas em `map/MapLayer.kt` (escolha salva em `AppSettings.mapLayer`).
   O usuário quis "ambos" (estilo e controles) e "vegetação" = imagem de satélite.
   Termos conferidos: o serviço gratuito da EOX (satélite) é não comercial e não prevê download
   de áreas, então o satélite tem `FLAG_NO_BULK` e fica fora de `OfflineMapManager` (só o cache
   do que foi visto). Pendente: o usuário pode perguntar à EOX (cloudless@eox.at) se um clube
   sem fins lucrativos pode baixar áreas; se sim, liberar o download do satélite. "Mapa
   simples" ficou de fora: os tiles do OpenStreetMap proíbem download para uso offline.
   Talvez depois: migrar para MapLibre (mapas vetoriais), mudança grande.
2. **Tela de abertura – feito (nov/2026)**: logo da bússola grande e "Path Finder" embaixo, fundo
   verde #2E5E3E (core-splashscreen + tela Compose curta, rota "splash" fora da pilha).
3. **Nova página inicial – feito (nov/2026)**: 4 botões grandes (grade 2×2): **Mapa** (desde o teste
   de campo, o mapa em tempo real `ui/livemap/`; a lista fica em `ui/trails/TrailsScreen.kt`), **Grupo** (rede local, painel do coordenador,
   navegar em grupo, painel do repetidor), **Configurações** e **Mensagens** (contador de não
   lidas); cartão "Em andamento" quando houver navegação ou gravação; links das notificações
   continuam funcionando. Grupo e Mensagens são do futuro Modo grupo: ainda sem bloqueio
   (`plans/Plans.kt`, `groupModeUnlocked` = true no piloto; aviso pronto na `HomeScreen`).
4. Teste de campo (`docs/teste-de-campo.md`) e ajustes a partir dos CSVs de diagnóstico.
   Primeira rodada feita (nov/2026); ajustes pedidos e **feitos**: zoom suave acima do 17,
   bandeiras de início e fim, botão Mapa abrindo o mapa em tempo real com várias trilhas ao
   mesmo tempo, tutorial "Como usar o app" e "Número na pista" na edição do ponto. Os CSVs de
   diagnóstico ainda não foram analisados. Mais detalhe que o zoom 17 só com outra fonte de
   mapa (vetorial/MapLibre ou satélite comercial).
5. Etapa extra (seção 9.1): internet via Firebase (envio duplo, gateway, sincronização).
   Depende de o usuário criar o projeto Firebase (`google-services.json`) e decidir sobre
   privacidade (dados de localização de menores; LGPD).
