plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.ceutenant.lumen"
version = "0.0.6"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Aponta pro Rider já instalado na máquina (path vem de
        // gradle.properties/riderInstallDir) em vez de baixar uma
        // distribuição própria pro sandbox de desenvolvimento — evita
        // duplicar ~1GB de download e qualquer fricção de licença pra rodar
        // o runIde local.
        local(providers.gradleProperty("riderInstallDir"))
    }
}

intellijPlatform {
    // instrumentCode (NotNull assertions / forms) tropeça tentando resolver
    // um "Packages" dentro do JAVA_HOME do Gradle nessa combinação de
    // versões — não precisamos disso pro spike, o plugin não tem forms nem
    // depende de instrumentação de bytecode.
    instrumentCode = false

    pluginConfiguration {
        id = "com.ceutenant.lumen"
        name = "Lumen"
        version = project.version.toString()

        description = """
            Pinta o gutter do editor com a cobertura de testes (Cobertura XML,
            gerado pelo coverlet.collector via `dotnet test --collect:"XPlat
            Code Coverage"`): verde pra linha coberta, vermelho pra não
            coberta, amarelo pra ponto de decisão (if/switch/&&/||) só
            parcialmente exercitado. Detecta e carrega o
            coverage.cobertura.xml mais recente sob a raiz do projeto
            automaticamente ao abrir; "Reload Coverage Report" reimporta sob
            demanda.
        """.trimIndent()

        // Texto simples pra "What's new" no Marketplace -- HTML puro (o
        // Marketplace não interpreta Markdown aqui), não vem de nenhum
        // CHANGELOG.md nem é gerado automaticamente. Atualizar manualmente
        // a cada versão nova, deixando o histórico das anteriores.
        changeNotes = """
            <b>0.0.6</b>
            <ul>
              <li>Corrigido: o botão de refresh dentro do painel não fazia nada ao clicar (a action não tinha o threading configurado corretamente e falhava silenciosamente).</li>
            </ul>
            <b>0.0.5</b>
            <ul>
              <li>Árvore do painel agora agrupa classes por namespace (Solution &gt; Projeto &gt; Namespace &gt; Classe), como no dotCover, em vez de por arquivo.</li>
            </ul>
            <b>0.0.4</b>
            <ul>
              <li>Corrigido bug em que as classes sumiam da árvore depois que relatórios de mais de um projeto de teste eram mesclados.</li>
            </ul>
            <b>0.0.3</b>
            <ul>
              <li>Adicionado nível de Classe na árvore do painel (Solution &gt; Projeto &gt; Arquivo &gt; Classe).</li>
            </ul>
            <b>0.0.2</b>
            <ul>
              <li>Corrigido: solution sem nenhum teste rodado agora mostra "No data" em vez de 100%, e lista os projetos existentes.</li>
            </ul>
            <b>0.0.1</b>
            <ul>
              <li>Primeira versão: gutter colorido por cobertura, painel com árvore Solution/Projeto/Arquivo, auto-refresh ao rodar os testes.</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "261"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    // Sobe uma instância headless do IDE só pra indexar telas de
    // configuração (Settings/Configurable) — o plugin ainda não tem
    // nenhuma, então é só overhead.
    buildSearchableOptions {
        enabled = false
    }
}
