# Coverage Gutter

Plugin de Rider (IntelliJ Platform) que pinta o gutter do editor com a
cobertura de testes — uma versão bem mais simples (e gratuita) do dotCover,
sem instrumentar nem rodar nada: só lê o Cobertura XML que o
`coverlet.collector` já gera via `dotnet test --collect:"XPlat Code Coverage"`.

- 🟩 verde — linha coberta
- 🟨 amarelo — ponto de decisão (`if`/`switch`/`&&`/`||`/ternário, inclusive
  o null-check embutido no `?.`) coberto, mas não em todos os caminhos
  possíveis (`condition-coverage < 100%` no XML)
- 🟥 vermelho — linha não coberta (`hits="0"`)

Ao abrir um projeto, procura o `coverage.cobertura.xml` mais recente sob a
raiz do projeto e carrega automaticamente. **"Reload Coverage Report"**
(menu de contexto do editor, ou menu Tools) reimporta sob demanda — rode os
testes de novo e chame essa ação pra ver o resultado atualizado.

## Por que existe

O dotCover cobra pra isso. A coleta de cobertura em si já é resolvida de
graça pelo `coverlet.collector` (referenciado nos `.csproj` de teste) — este
plugin só faz a parte de exibição, sem reinventar a coleta.

## Rodando localmente

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

## Estrutura

```
model/     — CoverageReport, LineHit, CoverageState (NOT_COVERED/PARTIALLY_COVERED/COVERED)
parser/    — CoberturaParser: XML -> CoverageReport
service/   — CoverageService: acha o XML mais recente, pinta/limpa o gutter (um serviço por projeto)
listener/  — CoverageEditorFactoryListener: pinta editor ao abrir, limpa ao fechar
startup/   — CoverageStartupActivity: carrega automaticamente quando o projeto abre
actions/   — ReloadCoverageAction: reimporta sob demanda
```

## Limitações conhecidas (v1)

- Só re-pinta quando você chama "Reload Coverage Report" manualmente — sem
  watcher no arquivo XML nem hook automático de pós-test-run.
- Sem toolwindow nem breakdown por classe/método — só o gutter.
- Testado até aqui com Rider 2026.1 (build RD-261.x) + relatórios do
  `coverlet.collector` 6.0.4. Outros geradores de Cobertura XML (dotnet-coverage,
  ReportGenerator) devem funcionar também já que o formato é o mesmo, mas não
  foram testados.
