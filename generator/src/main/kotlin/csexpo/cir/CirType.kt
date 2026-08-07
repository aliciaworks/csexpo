package csexpo.cir

/**
 * Type model of CIR. Mirrors SIR's SirType hierarchy (SirNominalType,
 * SirFunctionalType, ...) but targeted at C#.
 */
sealed interface CirType {
    val nullable: Boolean
}

/** Well-known Kotlin types that map directly onto C# primitives / BCL types. */
enum class CirBuiltinKind {
    INT, LONG, SHORT, BYTE, FLOAT, DOUBLE, BOOLEAN, CHAR,
    UINT, ULONG, USHORT, UBYTE,
    STRING, UNIT, ANY, NOTHING, ERROR,
    INT_ARRAY, LONG_ARRAY, SHORT_ARRAY, BYTE_ARRAY, FLOAT_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY, CHAR_ARRAY,
}

class CirBuiltinType(
    val kind: CirBuiltinKind,
    override val nullable: Boolean = false,
) : CirType

/**
 * A named type reference, e.g. `com.example.User` or `kotlin.collections.List`.
 * [fqName] keeps the fully qualified Kotlin name so the printer can decide the
 * C# namespace / collection mapping.
 */
class CirNominalType(
    val fqName: String,
    val simpleName: String,
    val typeArguments: List<CirType> = emptyList(),
    override val nullable: Boolean = false,
) : CirType

/** A function type; suspend functions map to Task-returning delegates. */
class CirFunctionType(
    val parameterTypes: List<CirType>,
    val returnType: CirType,
    val isSuspend: Boolean = false,
    override val nullable: Boolean = false,
) : CirType

/** A type parameter reference (e.g. `T`). */
class CirTypeParameterType(
    val name: String,
    override val nullable: Boolean = false,
) : CirType

/** Kotlin's `*` star projection. */
class CirStarProjection : CirType {
    override val nullable: Boolean = true
}

class CirTypeParameter(
    val name: String,
    val variance: CirVariance = CirVariance.INVARIANT,
    val upperBound: CirType? = null,
)

enum class CirVariance { INVARIANT, OUT, IN }

class CirParameter(
    val name: String,
    val type: CirType,
    val hasDefault: Boolean = false,
    val isVararg: Boolean = false,
)
