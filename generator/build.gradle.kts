// csexpo (C# Export) generator: reads a compiled Kotlin/Native klib (via the Kotlin
// Analysis API, exactly like JetBrains' Swift Export standalone) and emits C# bindings.
plugins {
    kotlin("jvm") version "2.4.10"
    application
}

group = "dev.csexpo"
version = "0.1.0"

repositories {
    mavenCentral()
    // com.android.tools.external.com-intellij:intellij-core (provides
    // kotlinx.coroutines.internal.intellij.IntellijCoroutines) lives on Google Maven.
    google()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")

    // Bundles (shaded) the Kotlin Analysis API, the klib reader
    // (createKaModulesForStandaloneAnalysis / getAllClassifiers) and
    // org.jetbrains.kotlin.library.metadata.KlibInputModule.
    implementation("org.jetbrains.kotlin:swift-export-embeddable:2.4.10")

    // The analysis API references compiler internals (Name, ClassId, Variance, ...).
    // swift-export-embeddable declares it as scope=runtime, so add it explicitly
    // to the compile classpath.
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")

    // Runtime: the embeddable's relocated IntelliJ core references
    // kotlinx.coroutines.internal.intellij.IntellijCoroutines, which is provided by
    // the com.android.tools intellij-core artifact (not the plain coroutines jar).
    runtimeOnly("com.android.tools.external.com-intellij:intellij-core:32.3.0")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("csexpo.generator.CSharpExportRunnerKt")
}

tasks.withType<JavaExec>().configureEach {
    maxHeapSize = "4g"
}
