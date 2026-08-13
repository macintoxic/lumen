# Lumen code coverage

🇧🇷 [Ler em português](#português)

## English

Rider (IntelliJ Platform) plugin that shows test coverage in the editor and
in a dedicated panel — a much simpler (and free) alternative to dotCover,
without instrumenting or running anything: it just reads the Cobertura XML
that `coverlet.collector` already generates via
`dotnet test --collect:"XPlat Code Coverage"`.

### Editor gutter

- 🟩 green — covered line
- 🟨 yellow — decision point (`if`/`switch`/`&&`/`||`/ternary, including the
  null-check built into `?.`) covered, but not on every possible path
  (`condition-coverage < 100%` in the XML)
- 🟥 red — uncovered line (`hits="0"`)

### "Code Coverage" panel (toolwindow, right sidebar)

Solution → Project (folder with a `.csproj`) → File → Class tree, in the
style of dotCover's "Coverage Tree" window: each row has a two-color bar
(green = covered, pink = uncovered, `%` written inside) and the
`uncovered/total` count of tracked lines. Double-clicking a file or class
opens the file in the editor. Compiler-generated nested types (async state
machines, lambda display classes) are folded into their containing class
instead of showing up as separate rows — same for real nested types
(private records/structs) — so the tree stays as clean as dotCover's instead
of listing every hidden type IL happens to generate.

### Automatic refresh

When a project opens, it looks for the most recent `coverage.cobertura.xml`
file(s) under the root (one per test project, grouped by the folder that
`dotnet test` doesn't recreate on every run — see
`CoverageService.findLatestReportFiles`) and loads them automatically. After
that, a VFS watcher keeps an eye on any `coverage.cobertura.xml` being
created/changed — it triggers a reload on its own, gutter and panel
together, whether the tests were run from Rider or from `dotnet test` in a
terminal (it doesn't depend on any specific run configuration).
**"Reload Coverage Report"** (editor context menu, Tools menu, or the
button in the panel) forces a re-import at any time.

### Why this exists

dotCover charges for this. The actual coverage collection is already solved
for free by `coverlet.collector` (referenced in the test `.csproj` files) —
this plugin only does the display part, without reinventing the collection.

### Running locally

Prerequisites:
- JDK 17+ to compile (the JBR that ships with Rider is usually too recent
  for Gradle to run directly — see the note below). Configure it in
  `~/.gradle/gradle.properties`:
  ```properties
  org.gradle.java.home=/path/to/jdk21
  ```
- If Rider isn't installed at
  `C:/Users/<you>/AppData/Local/Programs/Rider`, override
  `riderInstallDir` in your `~/.gradle/gradle.properties` or via
  `-PriderInstallDir=...`.

```bash
./gradlew buildPlugin   # packages into build/distributions/*.zip
./gradlew runIde        # boots a sandboxed Rider instance with the plugin installed
```

`instrumentCode` and `buildSearchableOptions` are disabled on purpose — the
plugin has no forms or settings screens, and those tasks were tripping over
the Rider 2026.1 + local JDK combination (see the commit history if you ever
need to re-enable one of them).

### Layout

```
model/     — CoverageReport/CoverageSummary, LineHit, CoverageState (NOT_COVERED/PARTIALLY_COVERED/COVERED)
parser/    — CoberturaParser: XML -> CoverageReport (preserves the real on-disk file casing for display)
service/   — CoverageService (finds the latest XML reports, paints/clears the gutter, aggregates the panel summary)
             CoverageFileWatcherService (VFS listener -> automatic reload)
             CoverageReloadListener (Topic — both gutter and panel subscribe)
listener/  — CoverageEditorFactoryListener: paints an editor on open, clears it on close
startup/   — CoverageStartupActivity: loads + starts the watcher when the project opens
actions/   — ReloadCoverageAction: re-imports on demand (context menu/Tools)
ui/        — CoverageToolWindowFactory/CoveragePanel: TreeTable Symbol/Coverage(%)/Uncovered-Total
             CoverageBarCellRenderer: hand-painted two-color bar (JProgressBar ignores
             `foreground` under Darcula — it always uses the theme's accent blue, hence not used here)
```

### Known limitations (v1)

- `CoverageService.summarize()` (locating each file's `.csproj` by walking
  up directory by directory via `listFiles()`) runs synchronously on the UI
  thread — fine for the repo size this was tested against, may become
  noticeable on much larger solutions.
- Tested so far with Rider 2026.1 (build RD-261.x) + `coverlet.collector`
  6.0.4 reports. Other Cobertura XML generators (dotnet-coverage,
  ReportGenerator) should work too since the format is the same, but they
  haven't been tested.
- The Marketplace/Plugin Verifier check flags deprecated/experimental API
  usage on `ToolWindowFactory` (`isApplicable`, `isDoNotActivateOnStart`,
  `getAnchor`, `getIcon`, `manage(...)`). This is unavoidable noise from
  simply implementing that interface — `CoverageToolWindowFactory` only
  overrides `createToolWindowContent`; those other members are default
  implementations on the interface itself, confirmed by decompiling
  `com.intellij.openapi.wm.ToolWindowFactory` from the Rider install (`javap
  -p -classpath <path-to-intellij.platform.ide.jar> com.intellij.openapi.wm.ToolWindowFactory`).
  Every toolwindow plugin registered the classic way (via `<toolWindow
  factoryClass=... anchor=... icon=...>` in `plugin.xml`, which is still
  fully supported) gets the same warning. It's informational, doesn't block
  publishing, and there's no source change that would remove it short of
  rewriting the whole factory around the newer coroutine-based
  registration API — disproportionate for a plugin with no async lifecycle
  needs.

---

## Português

Plugin de Rider (IntelliJ Platform) que mostra cobertura de testes no
editor e num painel dedicado — uma versão bem mais simples (e gratuita) do
dotCover, sem instrumentar nem rodar nada: só lê o Cobertura XML que o
`coverlet.collector` já gera via `dotnet test --collect:"XPlat Code Coverage"`.

### Gutter do editor

- 🟩 verde — linha coberta
- 🟨 amarelo — ponto de decisão (`if`/`switch`/`&&`/`||`/ternário, inclusive
  o null-check embutido no `?.`) coberto, mas não em todos os caminhos
  possíveis (`condition-coverage < 100%` no XML)
- 🟥 vermelho — linha não coberta (`hits="0"`)

### Painel "Code Coverage" (toolwindow, lateral direita)

Árvore Solution → Projeto (pasta com `.csproj`) → Arquivo → Classe, no
estilo da janela "Coverage Tree" do dotCover: cada linha tem uma barra
bicolor (verde = coberto, rosa = não coberto, `%` escrito dentro) e a
contagem `não-coberto/total` de linhas rastreadas. Duplo-clique num arquivo
ou classe abre o arquivo no editor. Tipos aninhados gerados pelo compilador
(state machine de método async, lambda) são dobrados pra dentro da classe
que os contém em vez de aparecer como linha própria na árvore — o mesmo
vale pra tipo aninhado de verdade (record/struct privado) — pra árvore
ficar tão limpa quanto a do dotCover em vez de listar todo tipo escondido
que o compilador gera.

### Atualização automática

Ao abrir um projeto, procura o(s) `coverage.cobertura.xml` mais recente(s)
sob a raiz (um por projeto de teste, agrupando pela pasta que o `dotnet
test` não recria a cada execução — ver `CoverageService.findLatestReportFiles`)
e carrega automaticamente. Depois disso, um watcher de VFS fica de olho em
qualquer `coverage.cobertura.xml` sendo criado/alterado — dispara reload
sozinho, gutter e painel juntos, tanto pra teste rodado pelo Rider quanto
por `dotnet test` no terminal (não depende de nenhuma run configuration
específica). **"Reload Coverage Report"** (menu de contexto do editor, menu
Tools, ou botão no painel) força a reimportação a qualquer momento.

### Por que existe

O dotCover cobra pra isso. A coleta de cobertura em si já é resolvida de
graça pelo `coverlet.collector` (referenciado nos `.csproj` de teste) — este
plugin só faz a parte de exibição, sem reinventar a coleta.

### Rodando localmente

Pré-requisitos:
- JDK 17+ pra compilar (JBR que vem com o Rider costuma ser recente demais
  pro Gradle rodar direto — ver nota abaixo). Configure em
  `~/.gradle/gradle.properties`:
  ```properties
  org.gradle.java.home=/caminho/pro/jdk21
  ```
- Se o Rider não estiver em
  `C:/Users/<você>/AppData/Local/Programs/Rider`, sobrescreva
  `riderInstallDir` no seu `~/.gradle/gradle.properties` ou via
  `-PriderInstallDir=...`.

```bash
./gradlew buildPlugin   # empacota em build/distributions/*.zip
./gradlew runIde        # sobe uma instância sandbox do Rider com o plugin instalado
```

`instrumentCode` e `buildSearchableOptions` ficam desligados de propósito —
o plugin não tem forms nem telas de configuração, e essas tasks tropeçavam
na combinação Rider 2026.1 + JDK local (ver histórico de commits se
precisar reativar algum dia).

### Estrutura

```
model/     — CoverageReport/CoverageSummary, LineHit, CoverageState (NOT_COVERED/PARTIALLY_COVERED/COVERED)
parser/    — CoberturaParser: XML -> CoverageReport (preserva o casing real do arquivo em disco pra exibição)
service/   — CoverageService (acha os XML mais recentes, pinta/limpa o gutter, agrega o resumo pro painel)
             CoverageFileWatcherService (VFS listener -> reload automático)
             CoverageReloadListener (Topic — gutter e painel assinam os dois)
listener/  — CoverageEditorFactoryListener: pinta editor ao abrir, limpa ao fechar
startup/   — CoverageStartupActivity: carrega + liga o watcher quando o projeto abre
actions/   — ReloadCoverageAction: reimporta sob demanda (menu de contexto/Tools)
ui/        — CoverageToolWindowFactory/CoveragePanel: TreeTable Symbol/Coverage(%)/Uncovered-Total
             CoverageBarCellRenderer: barra bicolor pintada à mão (JProgressBar não respeita
             `foreground` no Darcula — sempre usa o azul do tema, por isso não é usado aqui)
```

### Limitações conhecidas (v1)

- `CoverageService.summarize()` (localizar o `.csproj` de cada arquivo, via
  `listFiles()` subindo diretório por diretório) roda síncrono na thread da
  UI — tranquilo pro tamanho de repo testado aqui, pode ficar perceptível
  em solutions bem maiores.
- Testado até aqui com Rider 2026.1 (build RD-261.x) + relatórios do
  `coverlet.collector` 6.0.4. Outros geradores de Cobertura XML (dotnet-coverage,
  ReportGenerator) devem funcionar também já que o formato é o mesmo, mas não
  foram testados.
- A verificação do Marketplace/Plugin Verifier acusa uso de API
  depreciada/experimental em `ToolWindowFactory` (`isApplicable`,
  `isDoNotActivateOnStart`, `getAnchor`, `getIcon`, `manage(...)`). É ruído
  inevitável de simplesmente implementar essa interface — o
  `CoverageToolWindowFactory` só sobrescreve `createToolWindowContent`; os
  outros membros são implementações padrão da própria interface, confirmado
  decompilando `com.intellij.openapi.wm.ToolWindowFactory` direto da
  instalação do Rider (`javap -p -classpath <caminho-do-intellij.platform.ide.jar>
  com.intellij.openapi.wm.ToolWindowFactory`). Todo plugin de toolwindow
  registrado do jeito clássico (via `<toolWindow factoryClass=... anchor=...
  icon=...>` no `plugin.xml`, que continua totalmente suportado) recebe o
  mesmo aviso. É informativo, não bloqueia a publicação, e não há mudança de
  código que faça sumir sem reescrever o factory inteiro em torno da API de
  registro mais nova baseada em coroutines — desproporcional pra um plugin
  sem nenhuma necessidade de lifecycle assíncrono.
