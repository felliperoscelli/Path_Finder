# Path Finder – Especificação do Projeto

O Path Finder (trocadilho com Pathfinder, desbravador em inglês) é um aplicativo Android para criação de pistas de orientação (bússola), acompanhamento em tempo real dos monitores e comunicação entre aparelhos sem internet. Documento base para desenvolvimento no Claude Code.

## 1. Visão geral

- O coordenador cria a pista (pontos, azimutes, coordenadas, observações).
- Os grupos percorrem a pista usando bússola física. Somente o monitor de cada grupo usa o celular.
- O monitor segue a trilha gravada no app e é avisado se o grupo sair da rota.
- O coordenador vê a posição de todos os monitores num painel e recebe alertas.
- Tudo funciona sem internet e sem aparelhos extras, apenas com celulares.

## 2. Plataforma e tecnologia sugerida

- Android (iPhone talvez no futuro).
- Kotlin + Jetpack Compose.
- Banco local: Room (SQLite).
- Mapas: osmdroid com camada OpenTopoMap (curvas de nível), com download de área para uso offline.
- Localização: Fused Location Provider (GPS), serviço em primeiro plano para rastreamento contínuo.
- Declinação magnética: classe nativa `android.hardware.GeomagneticField`.
- Conversão UTM: biblioteca proj4j (ou implementação própria das fórmulas UTM/WGS84).
- Comunicação offline: Google Nearby Connections API (estratégia P2P\_CLUSTER), que combina Wi-Fi Direct e Bluetooth.
- PDF: `android.graphics.pdf.PdfDocument`.

## 3. Perfis de usuário

- Coordenador: cria e edita trilhas, distribui para monitores, acompanha o painel, recebe alertas, envia mensagens.
- Monitor: recebe a trilha, navega por ela, transmite sua posição, recebe alertas de desvio, envia mensagens e emergência.

## 4. Módulo 1 – Criação da trilha

- Botão "Nova trilha": inicia gravação do percurso pelo GPS.
- Detecção de parada: se o usuário ficar parado por cerca de 20 a 30 segundos (configurável), o app pergunta "Marcar ponto aqui?".
- Ao confirmar, o ponto é salvo com:
  - coordenadas em latitude/longitude (graus decimais) e em UTM (zona, E, N);
  - azimute verdadeiro e azimute magnético a partir do ponto anterior;
  - distância até o ponto anterior;
  - nome, observações e foto opcional.
- Pontos também podem ser adicionados manualmente a qualquer momento.
- Edição no mapa: mover, apagar, inserir pontos e reordenar. Azimutes e distâncias são recalculados automaticamente.
- Cálculo do azimute magnético: azimute verdadeiro menos a declinação local obtida com GeomagneticField (em Caldas Novas a declinação passa de 20° para oeste).
- Cada trilha guarda seus próprios limites de desvio (ver Módulo 2).

### Ficha imprimível (PDF)

- Modo completo: lista de pontos com coordenadas, azimute verdadeiro, azimute magnético, distância e observações.
- Modo só coordenadas: para pistas em que o grupo calcula os azimutes.
- Escolha do formato de coordenada: UTM, latitude/longitude ou ambos.
- Cabeçalho com nome da pista, data e declinação usada.

## 5. Módulo 2 – Navegação do monitor

- Mapa topográfico offline com a trilha, posição atual do monitor e próximo ponto.
- Indicação de distância e direção até o próximo ponto; ponto marcado como alcançado ao chegar perto dele.
- Alerta de desvio:
  - dispara quando o monitor está a mais de 30 metros da rota por 5 minutos seguidos (valores padrão);
  - distância e tempo editáveis nas configurações e salvos por trilha;
  - alerta local com som e vibração;
  - alerta enviado automaticamente ao painel do coordenador.
- Gravação do trajeto real percorrido pelo grupo, para revisão posterior.

## 6. Módulo 3 – Compartilhamento da trilha

- O coordenador envia a trilha para os monitores pela rede local (Nearby Connections), sem internet.
- Exportação/importação também por arquivo (GPX ou JSON), para envio por WhatsApp ou e-mail quando houver sinal.
- Cada monitor é associado a um grupo (nome do grupo e do monitor).

## 7. Módulo 4 – Painel do coordenador

- Mapa topográfico offline (curvas de nível) com a trilha e a posição de cada grupo.
- Ícone por grupo com nome, último ponto alcançado e tempo desde a última atualização.
- Alertas no topo da tela, com som e vibração, mostrando grupo, última posição e tempo fora da rota.
- Resposta rápida ao alerta (ex.: "Aguardem, resgate a caminho").
- Download prévio da área do mapa antes do evento.

## 8. Módulo 5 – Comunicação

- Mensagens de texto entre coordenador e monitores (geral e individual).
- Alertas rápidos pré-programados, por exemplo: "Grupo perdido", "Precisamos de ajuda", "Chegamos ao ponto", "Tudo certo".
- Botão de emergência bem visível, que envia alerta prioritário com a posição.
- Sem áudio.

## 9. Rede em malha offline

- Todos os celulares formam uma malha com Nearby Connections; cada aparelho repassa mensagens e posições dos outros.
- Alcance direto estimado: 100 a 200 metros em campo aberto, menos em mata.
- Pistas de até cerca de 2 km, com monitores e pontos intermediários servindo de repetidores.
- Opção: celulares antigos fixos nos pontos intermediários, ligados a power bank, funcionando como repetidores.
- Armazenar e encaminhar: mensagens não entregues ficam guardadas e são enviadas quando houver conexão.
- Cada mensagem tem ID único, origem, horário e contador de saltos, para evitar duplicação e loops.
- Alertas e emergência têm prioridade sobre atualizações de posição.
- Atualização de posição em intervalo configurável (ex.: a cada 30 segundos) para poupar bateria.

## 9.1 Modo híbrido (online + malha)

O app usa internet quando houver e cai para a rede em malha automaticamente, sem ação do usuário.

- Envio duplo: cada posição, mensagem e alerta segue pela internet (quando houver sinal) e pela malha (sempre).
- Servidor online sugerido: Firebase (Realtime Database ou Firestore), com cache offline nativo.
- Deduplicação: o ID único de cada mensagem faz o app descartar cópias que cheguem pelos dois caminhos.
- Gateway: se apenas um celular da malha tiver sinal, ele repassa para a internet tudo o que recebe da malha, e vice-versa.
- Acompanhamento remoto: com o gateway, o coordenador pode acompanhar o painel mesmo fora da área da pista.
- Indicador na tela: ícone mostrando o modo atual (online, malha ou ambos) e a quantidade de aparelhos conectados.
- Sincronização: ao voltar a internet, o histórico de posições e mensagens guardado localmente é enviado ao servidor.

## 10. Configurações

- Distância e tempo do alerta de desvio (padrão 30 m e 5 min).
- Tempo de parada para sugerir ponto.
- Intervalo de envio de posição.
- Formato de coordenadas padrão.
- Nome do usuário e perfil (coordenador ou monitor).

## 11. Etapas de desenvolvimento sugeridas

Etapa extra, depois da 6: integração com Firebase, envio duplo, deduplicação e gateway (seção 9.1).

1. Estrutura do projeto, banco local e mapa offline com OpenTopoMap.
2. Gravação de trilha, pontos, cálculos de azimute, declinação e UTM.
3. Edição de pontos e ficha em PDF.
4. Navegação do monitor e alerta de desvio local.
5. Comunicação Nearby Connections entre dois aparelhos.
6. Rede em malha com repasse e armazenamento.
7. Painel do coordenador, alertas remotos e mensagens.
8. Testes de campo e ajustes de bateria.
