package csexpo.generator.printer

import csexpo.cir.*

/**
 * Renders [CirType] values as C# type expressions. Mirrors the type-naming logic of
 * SIR's SirTypeNamer but for the C# type system.
 */
class CSharpTypeRenderer(private val currentNamespace: String) {

    fun render(type: CirType): String = when (type) {
        is CirBuiltinType -> renderBuiltin(type)
        is CirNominalType -> renderNominal(type)
        is CirFunctionType -> renderFunctionType(type)
        is CirTypeParameterType -> type.name
        is CirStarProjection -> "object?"
    }

    private fun renderBuiltin(t: CirBuiltinType): String {
        val base = when (t.kind) {
            CirBuiltinKind.INT -> "int"
            CirBuiltinKind.LONG -> "long"
            CirBuiltinKind.SHORT -> "short"
            CirBuiltinKind.BYTE -> "sbyte"
            CirBuiltinKind.FLOAT -> "float"
            CirBuiltinKind.DOUBLE -> "double"
            CirBuiltinKind.BOOLEAN -> "bool"
            CirBuiltinKind.CHAR -> "char"
            CirBuiltinKind.UINT -> "uint"
            CirBuiltinKind.ULONG -> "ulong"
            CirBuiltinKind.USHORT -> "ushort"
            CirBuiltinKind.UBYTE -> "byte"
            CirBuiltinKind.STRING -> "string"
            CirBuiltinKind.UNIT -> "void"
            CirBuiltinKind.ANY -> "object"
            CirBuiltinKind.NOTHING -> "object"
            CirBuiltinKind.ERROR -> "object"
            CirBuiltinKind.INT_ARRAY -> "int[]"
            CirBuiltinKind.LONG_ARRAY -> "long[]"
            CirBuiltinKind.SHORT_ARRAY -> "short[]"
            CirBuiltinKind.BYTE_ARRAY -> "sbyte[]"
            CirBuiltinKind.FLOAT_ARRAY -> "float[]"
            CirBuiltinKind.DOUBLE_ARRAY -> "double[]"
            CirBuiltinKind.BOOLEAN_ARRAY -> "bool[]"
            CirBuiltinKind.CHAR_ARRAY -> "char[]"
        }
        return if (t.nullable && base != "void") "$base?" else base
    }

    private fun renderNominal(t: CirNominalType): String {
        val args = t.typeArguments.map { render(it) }
        val base = when (t.fqName) {
            "kotlin.collections.List" -> "IReadOnlyList<${args.getOrElse(0) { "object?" }}>"
            "kotlin.collections.MutableList" -> "IList<${args.getOrElse(0) { "object?" }}>"
            "kotlin.collections.Set" -> "IReadOnlySet<${args.getOrElse(0) { "object?" }}>"
            "kotlin.collections.MutableSet" -> "ISet<${args.getOrElse(0) { "object?" }}>"
            "kotlin.collections.Map" ->
                "IReadOnlyDictionary<${args.getOrElse(0) { "object?" }}, ${args.getOrElse(1) { "object?" }}>"
            "kotlin.collections.MutableMap" ->
                "IDictionary<${args.getOrElse(0) { "object?" }}, ${args.getOrElse(1) { "object?" }}>"
            "kotlin.collections.Collection" -> "IReadOnlyCollection<${args.getOrElse(0) { "object?" }}>"
            "kotlin.Array" -> "${args.getOrElse(0) { "object?" }}[]"
            else -> renderNominalName(t)
        }
        return if (t.nullable && base != "void") "$base?" else base
    }

    private fun renderNominalName(t: CirNominalType): String {
        val pkg = t.fqName.substringBeforeLast('.')
        val simple = t.simpleName
        val name = if (pkg == currentNamespace || pkg.isEmpty()) simple else "$pkg.$simple"
        val args = if (t.typeArguments.isNotEmpty()) {
            "<" + t.typeArguments.joinToString(", ") { render(it) } + ">"
        } else {
            ""
        }
        return name + args
    }

    private fun renderFunctionType(t: CirFunctionType): String {
        val paramTypes = t.parameterTypes.map { render(it) }
        val retRaw = render(t.returnType)
        val retCs = when {
            t.isSuspend && retRaw == "void" -> "Task"
            t.isSuspend -> "Task<$retRaw>"
            else -> retRaw
        }
        val all = paramTypes + listOf(retCs)
        return "Func<${all.joinToString(", ")}>"
    }
}
