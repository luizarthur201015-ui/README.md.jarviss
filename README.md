# JARVIS AI — Android

Assistente Android experimental com HUD holográfico circular animado, comandos locais, entrada por voz PT-BR, fala via Android TextToSpeech e conexão opcional a uma API compatível com OpenAI Chat Completions.

## Recursos
- Interface sci-fi azul/ciano, anéis circulares animados e estados **standby / ouvindo / processando**.
- Conversa por texto ou voz; respostas faladas em português.
- Comandos: hora/data, notas persistentes, calculadora com parênteses, pesquisa na web e abertura de alguns apps.
- API opcional configurável no botão ⚙.
- Workflow de GitHub Actions para gerar o APK debug.

## Compilar APK
Envie o conteúdo desta pasta à raiz do repositório e abra **Actions → Build JARVIS AI APK**. Baixe `JARVIS-AI-debug-APK` nos artefatos da execução.

## Importante
Conversa aberta requer internet e uma API compatível. A chave configurada fica guardada localmente no aparelho; não a compartilhe. O Android limita os comandos que um app pode executar. O APK precisa ser compilado pelo workflow; ele não está incluído nesta fonte.
