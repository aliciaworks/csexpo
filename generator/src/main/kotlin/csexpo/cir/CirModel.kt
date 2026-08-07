package csexpo.cir

/**
 * CIR — "C# Intermediate Representation".
 *
 * A language-neutral model of a Kotlin module's public API, mirroring JetBrains'
 * SIR (Swift Intermediate Representation; see reference/kotlin/native/swift/sir)
 * but targeted at C#. A [csexpo.generator.translate.ModuleTranslator] builds it
 * from a compiled Kotlin/Native klib through the Analysis API, and a
 * [csexpo.generator.printer.CSharpPrinter] turns it into .cs source files.
 */
class CirModule(
    val name: String,
    val files: MutableList<CirFile> = mutableListOf(),
)

/** One Kotlin package, rendered to one .g.cs file (or a set of nested namespaces). */
class CirFile(
    val packageName: String,
    val declarations: MutableList<CirDeclaration> = mutableListOf(),
)

enum class CirVisibility { PUBLIC, INTERNAL, PRIVATE, PROTECTED }

/** Root of the declaration hierarchy. Mirrors SIR's SirDeclaration. */
sealed interface CirDeclaration {
    val name: String
    val visibility: CirVisibility
    val documentation: String?
}

/** Mirrors SIR's SirClass: a class, interface, data class, object or abstract class. */
class CirClass(
    override val name: String,
    override val visibility: CirVisibility = CirVisibility.PUBLIC,
    override val documentation: String? = null,
    val isData: Boolean = false,
    val isInterface: Boolean = false,
    val isAbstract: Boolean = false,
    val isSealed: Boolean = false,
    val isObject: Boolean = false,
    val superClass: CirNominalType? = null,
    val interfaces: List<CirNominalType> = emptyList(),
    val typeParameters: List<CirTypeParameter> = emptyList(),
    val constructorParameters: List<CirParameter> = emptyList(),
    val declarations: MutableList<CirDeclaration> = mutableListOf(),
) : CirDeclaration

/** Mirrors SIR's SirEnum: an enum class with its cases. */
class CirEnum(
    override val name: String,
    override val visibility: CirVisibility = CirVisibility.PUBLIC,
    override val documentation: String? = null,
    val cases: List<CirEnumCase> = emptyList(),
    val declarations: MutableList<CirDeclaration> = mutableListOf(),
) : CirDeclaration

class CirEnumCase(
    val name: String,
    /** Raw value when it can be statically determined (const), otherwise null. */
    val rawValue: String? = null,
)

/** Mirrors SIR's SirFunction. */
class CirFunction(
    override val name: String,
    override val visibility: CirVisibility = CirVisibility.PUBLIC,
    override val documentation: String? = null,
    val isStatic: Boolean = false,
    val isInstance: Boolean = true,
    val isSuspend: Boolean = false,
    val isOperator: Boolean = false,
    val isOverride: Boolean = false,
    val receiverType: CirType? = null,
    val typeParameters: List<CirTypeParameter> = emptyList(),
    val parameters: List<CirParameter> = emptyList(),
    val returnType: CirType = CirBuiltinType(CirBuiltinKind.UNIT),
) : CirDeclaration

/** Mirrors SIR's getter/setter-backed property declarations. */
class CirProperty(
    override val name: String,
    override val visibility: CirVisibility = CirVisibility.PUBLIC,
    override val documentation: String? = null,
    val type: CirType = CirBuiltinType(CirBuiltinKind.ANY),
    val isReadOnly: Boolean = true,
    val isStatic: Boolean = false,
    val isInstance: Boolean = true,
    val isOverride: Boolean = false,
    val hasGetter: Boolean = true,
    val hasSetter: Boolean = false,
) : CirDeclaration

/** Mirrors SIR's SirTypealias. */
class CirTypealias(
    override val name: String,
    override val visibility: CirVisibility = CirVisibility.PUBLIC,
    override val documentation: String? = null,
    val target: CirType,
) : CirDeclaration
