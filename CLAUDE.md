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
- `ui/` telas Compose por pasta (home, map, points, navigate, dashboard, messages, results,
  network, diagnostics, offline, settings) e `components/` compartilhados.

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

## Próximos passos combinados

1. **Aparência do mapa** (pedido do usuário: "mais no estilo do Google Maps" e opção de
   alternar entre curvas de nível e vegetação). Plano proposto:
   - interface no estilo Google: botões redondos flutuantes (localização, bússola/norte,
     camadas), ponto azul com direção, pinos numerados, rotação do mapa;
   - seletor de camadas com miniaturas: Curvas de nível (OpenTopoMap), Satélite/vegetação
     (candidato: Sentinel-2 cloudless da EOX, uso não comercial, ~10 m) e um Mapa simples;
     **confirmar os termos de uso offline de cada fornecedor antes de implementar**;
   - talvez depois: migrar para MapLibre (mapas vetoriais), mudança grande.
   Perguntas em aberto para o usuário: o que mais incomodou (estilo do OpenTopoMap, botões e
   marcadores, ou ambos) e se "vegetação" é imagem de satélite ou mapa desenhado.
2. Teste de campo (`docs/teste-de-campo.md`) e ajustes a partir dos CSVs de diagnóstico.
3. Etapa extra (seção 9.1): internet via Firebase (envio duplo, gateway, sincronização).
   Depende de o usuário criar o projeto Firebase (`google-services.json`) e decidir sobre
   privacidade (dados de localização de menores; LGPD).
