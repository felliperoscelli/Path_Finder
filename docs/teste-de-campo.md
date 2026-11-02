# Path Finder – Roteiro de teste de campo

Objetivo: confirmar em situação real o que não dá para testar no computador (GPS, rede entre
celulares, alarmes, bateria) e coletar dados para os ajustes finais.

## Antes de sair

1. Instale o mesmo APK em todos os celulares.
2. Em **Configurações**, em cada aparelho:
   - nome e perfil (1 Coordenador, 2 ou mais Monitores, e se possível 1 Repetidor);
   - o **mesmo código do evento** (ex.: `TESTE1`);
   - **Permitir ensaio com caminhada simulada** ligado (para o ensaio em casa).
3. No coordenador: crie uma pista curta (5 pontos, 500 m a 1 km), defina uma hora limite
   próxima (ex.: daqui a 1 h) e baixe a área do mapa em **Mapas offline**.
4. Abra **Diagnóstico de campo** em todos e toque em **Limpar** para começar do zero.
5. Carregue todos os celulares até 100% e anote o modelo de cada um.

## Ensaio em casa (15 min, com todos os celulares na mesa)

1. Ligue a **Rede local** em todos; confira se aparecem "Conectado".
2. Coordenador: envie a trilha (lista de pontos → compartilhar → Enviar).
3. Monitor: abra a trilha → **Ensaiar** a 20x com "Forçar desvio".
4. Confira no coordenador: grupo [SIM] no **Painel**, alarme de desvio, volta à rota, pontos
   alcançados, e o **Resultado** depois de o monitor encerrar.
5. Teste mensagens, alerta rápido "Precisamos de ajuda", **Emergência** e "Mandar voltar à base".

## No campo

| # | Teste | Como | Resultado esperado |
|---|---|---|---|
| 1 | Gravação da trilha | Coordenador grava a pista andando, parando 30 s em cada ponto | Pergunta "Marcar ponto aqui?" em cada parada |
| 2 | Alcance direto | Monitor se afasta do coordenador em campo aberto; anotar a distância em que a rede cai | 100–200 m em campo aberto; menos em mata |
| 3 | Repasse (malha) | Coordenador numa ponta, repetidor no meio, monitor na outra ponta (fora do alcance direto do coordenador) | Posição do monitor chega ao painel; "repassadas" sobe no repetidor |
| 4 | Armazenar e encaminhar | Monitor sai do alcance de todos por 5 min e volta | Ao voltar, o painel atualiza; alertas do período chegam |
| 5 | Chegada aos pontos | Navegar a pista com o monitor | Som e vibração em cada ponto (raio 15 m) |
| 6 | Alerta de desvio | Sair 50 m da rota por 5 min (ou reduzir para 1 min na trilha) | Alarme no monitor e no coordenador; some ao voltar |
| 7 | Sem notícias | Desligar a rede do monitor por mais de 10 min | Alarme "Sem notícias" no coordenador |
| 8 | Hora limite | Deixar passar a hora limite | Aviso 30 min antes e alarme na hora, nos dois lados |
| 9 | Emergência | Monitor toca Emergência | Alarme repetido no coordenador até "Ciente" |
| 10 | Voltar à base | Coordenador manda o grupo voltar | Monitor é guiado até a base |
| 11 | Tela no sol | Ler a navegação ao sol com e sem alto contraste | Legível com alto contraste |
| 12 | Bateria | Usar 2 h seguidas sem carregar | Ver %/hora no Diagnóstico de cada celular |

## Depois do teste

1. Em cada celular: **Configurações → Diagnóstico de campo → Exportar (.csv)** e envie os
   arquivos (WhatsApp ou e-mail).
2. Anote junto: modelo do celular, perfil, onde ficou (mata, campo aberto) e o que deu
   errado ou estranho, com o horário aproximado (o registro tem data e hora de tudo).
3. Com esses arquivos dá para ajustar: raio de chegada, tempo do alerta de desvio, intervalo de
   posição, limites de "sem notícias" e de economia de bateria.

## O que o registro (CSV) contém

`data_hora;tipo;detalhe`, abre em planilha. Tipos:

- `BATERIA` a cada 5 min (% e consumo por hora);
- `GPS` por minuto (quantidade de leituras e precisão), `GPS_SIMULADO` no ensaio;
- `REDE` conexões e quedas; `MALHA` a cada 5 min (mensagens criadas, recebidas, repassadas,
  enviadas e guardadas);
- `ENERGIA` mudanças da economia de bateria; `ALERTA` cada alarme; `NAVEGACAO` início e fim.
