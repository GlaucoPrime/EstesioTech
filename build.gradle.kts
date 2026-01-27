// Arquivo de Configuração GLOBAL do Projeto (Raiz)
// Aqui definimos as versões dos plugins para que todos os módulos usem as mesmas.

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Classpath para navegação segura (Safe Args), se usar no futuro
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7")
    }
}

plugins {
    // Plugin Android Application: Define a versão 8.2.0 (Estável)
    // 'apply false' significa: "Baixe essa versão, mas não aplique na raiz, apenas nos módulos filhos"
    id("com.android.application") version "8.13.2" apply false

    // Plugin Kotlin Android: Versão 1.9.0 (Compatível com Compose Compiler 1.5.1)
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false

    // Plugin Google Services (Firebase): Versão 4.4.0
    // Essencial para conectar ao Firebase
    id("com.google.gms.google-services") version "4.4.0" apply false
}

// Tarefa de limpeza padrão (opcional, mas boa prática)
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}