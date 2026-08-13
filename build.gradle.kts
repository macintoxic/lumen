plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.ceutenant.lumen"
version = "0.0.3"

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
