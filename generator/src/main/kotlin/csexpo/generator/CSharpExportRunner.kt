package csexpo.generator

import csexpo.generator.printer.CSharpPrinter
import csexpo.generator.translate.ModuleTranslator
import org.jetbrains.kotlin.analysis.api.klib.reader.createKaModulesForStandaloneAnalysis
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.library.metadata.KlibInputModule
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import java.io.File
import kotlin.system.exitProcess

/**
 * Per-input configuration carried into [KlibInputModule]. Kept empty; the generator
 * needs no per-module options yet.
 */
class InputConfig

/**
 * Entry point. Mirrors JetBrains' SwiftExportRunner
 * (reference/kotlin/native/swift/swift-export-standalone): load the compiled klib
 * through the Analysis API, translate the public API to a CIR module, and print C#.
 *
 * Usage:
 *   --klib <path-to-klib> [--output <dir>] [--konan-home <path>] [--target mingwX64|...]
 */
fun main(args: Array<String>) {
    val opts = parseArgs(args)

    val klibFile = File(opts["--klib"] ?: error("Missing required --klib <path-to-klib>"))
    require(klibFile.exists()) { "klib not found: $klibFile" }

    val outputDir = File(opts["--output"] ?: "generated")
    val konanHome = (opts["--konan-home"]?.let { File(it) } ?: findKonanHome())
        ?: error("Kotlin/Native distribution not found. Build kmp-lib first (downloads the toolchain) or pass --konan-home.")

    // Windows host target; configurable later (KonanTarget valueOf is brittle across versions).
    val target = KonanTarget.MINGW_X64
    val stdlib = findStdlib(konanHome)
        ?: error("stdlib klib not found under $konanHome (looked in klib/common/stdlib and klib/platform/*/stdlib)")

    val stdlibModule = KlibInputModule("KotlinStdlib", stdlib.toPath(), InputConfig())
    val userModule = KlibInputModule("KmpLib", klibFile.toPath(), InputConfig())

    println("Input klib     : $klibFile")
    println("Kotlin/Native  : $konanHome")
    println("Target         : $target")
    println("Stdlib         : $stdlib")
    println("Output dir     : $outputDir")

    // The IntelliJ application spawned by the analysis session keeps non-daemon
    // threads alive; exit explicitly so the CLI returns instead of hanging.
    val kaModules = createKaModulesForStandaloneAnalysis(
        inputs = listOf(stdlibModule, userModule),
        targetPlatform = NativePlatforms.nativePlatformBySingleTarget(target),
    )
    // The user module is the only input that is not the stdlib.
    val main = kaModules.mainModules.first { it.libraryName != "KotlinStdlib" }
    val cir = ModuleTranslator().translate(useSiteModule = kaModules.useSiteModule, target = main)
    CSharpPrinter().print(cir, outputDir)

    println()
    println("Done. Generated C# bindings into: $outputDir")
    exitProcess(0)
}

// ------------------------------------------------------------------ helpers

private fun parseArgs(args: Array<String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--")) {
            val value = args.getOrNull(i + 1)
            if (value != null && !value.startsWith("--")) {
                map[arg] = value
                i += 2
            } else {
                map[arg] = ""
                i += 1
            }
        } else {
            i += 1
        }
    }
    return map
}

private fun findKonanHome(): File? {
    val dataDir = System.getenv("KONAN_DATA_DIR")?.let { File(it) }
        ?: File(System.getProperty("user.home"), ".konan")
    if (!dataDir.isDirectory) return null
    return dataDir.listFiles { f -> f.isDirectory && f.name.startsWith("kotlin-native-prebuilt-") }
        ?.maxByOrNull { it.name }
}

private fun findStdlib(konanHome: File): File? {
    val common = File(konanHome, "klib/common/stdlib")
    if (common.exists()) return common
    val platform = File(konanHome, "klib/platform")
    if (platform.isDirectory) {
        platform.listFiles()?.forEach { targetDir ->
            val candidate = File(targetDir, "stdlib")
            if (candidate.exists()) return candidate
        }
    }
    return null
}
