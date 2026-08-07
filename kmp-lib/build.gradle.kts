// Sample Kotlin Multiplatform library that will be exported to C# by the csexpo generator.
// The klib produced by compileKotlinMingwX64 is the input to the generator.
plugins {
    kotlin("multiplatform") version "2.4.10"
}

repositories {
    mavenCentral()
}

kotlin {
    // Windows host target: compile-only for now (klib). Linking a shared library
    // additionally requires the MSVC toolchain, which is a later milestone.
    mingwX64 {
        binaries {
            sharedLib {
                baseName = "csexpo"
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
            }
        }
    }
}
