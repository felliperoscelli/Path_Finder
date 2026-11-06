package com.pathfinder.ui.tutorial

/** Uma página do tutorial: título e os parágrafos curtos (um por item). */
data class TutorialPage(val topic: TutorialTopic, val title: String, val items: List<String>)

/** Assunto de cada página (escolhe o ícone). */
enum class TutorialTopic { WELCOME, MAP, OFFLINE, CREATE, EDIT, NAVIGATE, GROUP, COORDINATOR, TIPS }

/** Texto do tutorial "Como usar o app", na ordem em que as coisas são feitas antes de um evento. */
object TutorialContent {
    val pages = listOf(
        TutorialPage(
            TutorialTopic.WELCOME,
            "Bem-vindo ao Path Finder",
            listOf(
                "App de pistas de orientação para clubes: o coordenador cria a pista e os monitores navegam com alerta de desvio.",
                "Funciona sem internet: os mapas são baixados antes e os celulares conversam entre si por Bluetooth e Wi-Fi Direct.",
                "Primeiro passo: em Configurações, coloque seu nome e escolha o perfil (Coordenador, Monitor ou Repetidor).",
            ),
        ),
        TutorialPage(
            TutorialTopic.MAP,
            "Mapa",
            listOf(
                "O botão Mapa mostra onde você está, em tempo real: o ponto azul é você e a seta indica para onde está indo.",
                "Botões à direita: camadas (curvas de nível ou satélite), bússola (toque para deixar o norte para cima) e minha localização. Gire o mapa com dois dedos.",
                "Toque em \"Trilhas\" para escolher quais trilhas aparecem. Dá para mostrar várias ao mesmo tempo, cada uma com uma cor.",
                "A bandeira verde marca o início da pista e a quadriculada, o fim. Toque num ponto para abrir a trilha ou navegar.",
            ),
        ),
        TutorialPage(
            TutorialTopic.OFFLINE,
            "Mapas sem internet",
            listOf(
                "Sem internet o mapa só mostra o que foi baixado antes. Faça isso com Wi-Fi, antes do evento.",
                "No Mapa, toque no ícone de download (Mapas offline), posicione o mapa sobre a área do evento, escolha os zooms e baixe.",
                "O satélite não é baixado por área: abra a região em satélite com internet, que o que você viu fica guardado no celular.",
                "Os mapas têm detalhe até o zoom 17 (escala de uns 50 m). Mais perto que isso a imagem é só ampliada.",
            ),
        ),
        TutorialPage(
            TutorialTopic.CREATE,
            "Criar uma trilha",
            listOf(
                "No Mapa, toque em Trilhas → Gerenciar → Nova trilha (ou importe um arquivo .gpx ou .json pelo menu ⋮).",
                "No mapa da trilha, \"Gravar\" registra o caminho pelo GPS enquanto você anda. Quando você para, o app pergunta \"Marcar ponto aqui?\".",
                "Também dá para marcar um ponto na posição atual, tocar e segurar no mapa para pôr um ponto em qualquer lugar, ou digitar as coordenadas (UTM ou latitude/longitude).",
            ),
        ),
        TutorialPage(
            TutorialTopic.EDIT,
            "Editar, imprimir e enviar",
            listOf(
                "O lápis liga o modo edição: arraste os pontos para mudá-los de lugar e toque num ponto para renomear, apagar ou corrigir as coordenadas.",
                "A lista de pontos mostra distâncias e azimutes, muda a ordem dos pontos, gera a ficha em PDF e exporta a trilha.",
                "Em \"Enviar trilha\" o coordenador manda a pista para os monitores pela rede local.",
                "Em editar trilha: limites do alerta de desvio, hora limite, modo desafio e declinação.",
            ),
        ),
        TutorialPage(
            TutorialTopic.NAVIGATE,
            "Navegar",
            listOf(
                "Toque na bússola da trilha (no perfil Monitor, basta tocar na trilha). Informe o grupo e comece.",
                "A tela mostra o próximo ponto em verde, a distância, o azimute e uma seta que aponta para ele.",
                "Ao chegar perto do ponto (15 m) o celular toca e vibra. Se o grupo ficar fora da rota por muito tempo, toca o alarme de desvio.",
                "No modo desafio o monitor não vê distância nem direção: o app só avisa a chegada e o desvio.",
            ),
        ),
        TutorialPage(
            TutorialTopic.GROUP,
            "Grupo e rede local",
            listOf(
                "Em Grupo, ligue a rede local: os celulares se conectam sem internet, com alcance de 100 a 200 m em campo aberto.",
                "Use o mesmo código do evento em todos os celulares (Configurações → Evento).",
                "As mensagens passam de celular em celular e ficam guardadas até alguém chegar perto, então quem está longe também recebe.",
                "Repetidor: um celular deixado no meio da pista, ligado a um power bank, só para repassar as mensagens.",
            ),
        ),
        TutorialPage(
            TutorialTopic.COORDINATOR,
            "Painel, mensagens e emergência",
            listOf(
                "O coordenador acompanha os grupos no Painel (Grupo → Painel do coordenador): verde na rota, laranja afastado, vermelho fora da rota, cinza sem notícias.",
                "Mensagens vão para todos ou para um grupo. O monitor tem alertas rápidos, como \"Precisamos de ajuda\".",
                "Emergência: o monitor toca o botão e o coordenador recebe um alarme repetido até tocar \"Ciente\".",
                "O coordenador pode mandar um grupo voltar à base. No fim, o Resultado (troféu na lista de pontos) mostra a classificação dos grupos.",
            ),
        ),
        TutorialPage(
            TutorialTopic.TIPS,
            "Dicas para o dia do evento",
            listOf(
                "Carregue todos os celulares e baixe o mapa da área antes de sair.",
                "Para ler no sol, ligue o alto contraste em Configurações.",
                "Faça um ensaio em casa: ligue \"Permitir ensaio com caminhada simulada\" em Configurações e use \"Ensaiar\" na navegação.",
                "Este tutorial fica no botão \"Como usar o app\" da página inicial.",
            ),
        ),
    )
}
