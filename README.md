# Usagi Thumb 🐰

Editor de miniaturas (thumbnails) 1280×720 para o canal UsagiNoYami. Kotlin + Jetpack Compose, tema vermelho e preto.

## Versão 0.2 (novidades)
- **Projetos salvos** (aba *Projetos*): salva camadas, fundo e imagens no próprio celular, lista com miniatura, abrir, apagar e "salvar como novo". Ao abrir/criar outro projeto o atual é salvo antes; ao sair do app um projeto já salvo é atualizado sozinho
- **Contorno sticker**: botão *Sticker* em qualquer imagem (espessura e cor). Recorte o fundo antes (✂) para a borda seguir a silhueta
- **Prévia estilo YouTube** (▶ Prévia): a miniatura grande, média e bem pequena, com o horário no canto, título editável e tema claro/escuro
- **Formas e adesivos** (aba *Adicionar*): seta, círculo, retângulo e estrela (cor, preenchida ou só contorno, largura, altura, brilho) e 18 emojis de reação

## Versão 0.1
- **Templates 1280×720**: Usagi clássico, Reação, Versus, Tutorial, Texto gigante e Em branco (aba *Templates*)
- **Texto com brilho e contorno**: 6 fontes, tamanho, cor, espessura e cor do contorno, intensidade e cor do brilho (estilo neon). Várias linhas com Enter
- **Imagens por cima**: ＋ Imagem (seletor de fotos do Android), 🐰 Mascote (já recortado) e "Compartilhar → Usagi Thumb" direto de qualquer app de fotos
- **Gestos**: arrastar move; 2 dedos dão zoom e giram; ímã no centro (linhas pontilhadas); lista de camadas para escolher uma que ficou escondida
- **Camadas**: frente, trás, centralizar, duplicar, apagar, opacidade, espelhar, "Preencher tela", "Preto some" (o preto da imagem fica transparente)
- **Recortar fundo** (✂ em qualquer imagem):
  - *Varinha*: toque numa cor e ela some (com tolerância; "Só região ligada" liga/desliga)
  - *Fundo automático*: apaga a cor dos 4 cantos
  - *Borracha* e *Restaurar* com zoom de 2 dedos para acertar o detalhe
  - *Suavizar bordas*, *Cortar sobra*, *Desfazer*
- **Fundo**: sólido, degradê (com ângulo) ou radial
- **Desfazer/refazer** (↶ ↷) em tudo
- **Exportar** JPG ou PNG em 1280×720. No JPG o app escolhe sozinho a melhor qualidade que cabe em **2 MB** (limite do YouTube). Salva em *Imagens/UsagiThumb* e/ou compartilha
- 3 temas (🎨): vermelho, violeta, ciano
- Sem permissões e sem internet. Nada sai do aparelho

## Como abrir
Android Studio → **Open** → pasta `usagi-thumb` → rodar no celular (Android 10 ou mais novo).

## Estrutura
- `ProjectJson.kt` — salvar/abrir projeto em JSON (Kotlin puro, testado)
- `ProjectStore.kt` — pasta dos projetos salvos (JSON + PNG + miniatura)
- `ExtraPanels.kt` — abas Adicionar e Projetos, propriedades das formas
- `PreviewScreen.kt` — prévia estilo YouTube
- `ThumbLogic.kt` — camadas, formas, templates, gestos, recorte por cor, limite de 2 MB (Kotlin puro, testado)
- `Renderer.kt` — desenha tudo (a prévia e o arquivo final usam o mesmo código)
- `ThumbIo.kt` — ler imagens, JPG/PNG, salvar na galeria
- `ThumbViewModel.kt` — estado, desfazer, recorte, exportação
- `ThumbScreens.kt` — editor, painéis, templates, exportar
- `CutoutScreen.kt` — tela de recorte
- `Components.kt` — botões, sliders, cores
- `MainActivity.kt` — seletor de fotos, "Compartilhar para", compartilhar o resultado
- `core-theme/` — tema compartilhado (cópia)

## Notas
- O recorte funciona por cor (sem IA): ótimo para fundo liso ou preto; para fundo complicado use a borracha
- Imagens importadas são reduzidas para no máximo 1280 px no maior lado
- Mascote recortado a partir da arte original (fundo preto removido)
- Um PNG com muita imagem/foto pode passar de 2 MB; nesse caso o app avisa para usar JPG

## Próximas versões
- [ ] Mais templates e fontes
- [ ] Recorte automático de pessoas (precisa de modelo de IA)
