@file:OptIn(
    org.jetbrains.kotlin.analysis.api.KaContextParameterApi::class,
    org.jetbrains.kotlin.analysis.api.KaExperimentalApi::class,
    org.jetbrains.kotlin.analysis.api.KaNonPublicApi::class,
)

package csexpo.generator.translate

import csexpo.cir.*
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.combinedDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.components.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.export.utilities.getKDocString
import org.jetbrains.kotlin.analysis.api.export.utilities.isSuspend
import org.jetbrains.kotlin.analysis.api.klib.reader.getAllClassifiers
import org.jetbrains.kotlin.analysis.api.klib.reader.getAllDeclarations
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.Variance

/**
 * Translates the public API of a compiled Kotlin/Native klib (exposed through the
 * Kotlin Analysis API) into a [CirModule]. Mirrors JetBrains' SIR providers
 * (reference/kotlin/native/swift/sir-providers) but targets C#.
 *
 * All translation runs inside a single `analyze {}` session, exactly like the
 * Swift Export standalone (`translateModule` in
 * swift-export-standalone/.../builders/buildSwiftModule.kt).
 */
class ModuleTranslator {

    /**
     * [useSiteModule] is the session's use-site module (a source module provided by
     * `createKaModulesForStandaloneAnalysis`); [target] is the compiled library whose
     * public API is translated. Mirrors Swift Export's `translateModule`, which
     * `analyze(useSiteModule)`s and enumerates the target library inside the session.
     */
    fun translate(useSiteModule: KaModule, target: KaLibraryModule): CirModule = analyze(useSiteModule) {
        val byPackage = mutableMapOf<FqName, MutableList<CirDeclaration>>()

        // 1) Classifiers: classes, interfaces, enums, objects, typealiases.
        for (symbol in target.getAllClassifiers()) {
            val declaration = when (symbol) {
                is KaNamedClassSymbol -> symbol.toCirClass()
                is KaTypeAliasSymbol -> symbol.toCirTypealias()
                else -> null
            }
            if (declaration != null) {
                val pkg = when (symbol) {
                    is KaNamedClassSymbol -> symbol.classId
                    is KaTypeAliasSymbol -> symbol.classId
                    else -> null
                }?.packageFqName ?: continue
                byPackage.getOrPut(pkg) { mutableListOf() } += declaration
            }
        }

        // 2) Top-level callables (functions and properties at package level).
        for (callable in target.getAllDeclarations().filterIsInstance<KaCallableSymbol>()) {
            if (callable.origin != KaSymbolOrigin.SOURCE && callable.origin != KaSymbolOrigin.LIBRARY) continue
            if (callable.visibility != KaSymbolVisibility.PUBLIC) continue
            val declaration = callable.toCirTopLevelDeclaration() ?: continue
            val pkg = callable.callableId?.packageName ?: continue
            byPackage.getOrPut(pkg) { mutableListOf() } += declaration
        }

        val cir = CirModule(target.libraryName)
        for ((pkg, declarations) in byPackage) {
            cir.files += CirFile(packageName = pkg.asString(), declarations = declarations)
        }
        cir
    }

    // ------------------------------------------------------------------ classes

    context(session: KaSession)
    private fun KaNamedClassSymbol.toCirClass(): CirDeclaration? {
        if (classKind == KaClassKind.ANNOTATION_CLASS) return null
        if (classKind == KaClassKind.COMPANION_OBJECT) return null
        if (visibility != KaSymbolVisibility.PUBLIC) return null

        val name = name?.asString() ?: "Unnamed"
        val vis = visibility.toCirVisibility()
        val doc = getKDocString()

        return when (classKind) {
            KaClassKind.ENUM_CLASS -> CirEnum(
                name = name,
                visibility = vis,
                documentation = doc,
                // Enum entries are exposed through the combined declared member scope.
                cases = (this.combinedDeclaredMemberScope.callables + this.combinedDeclaredMemberScope.classifiers)
                    .filterIsInstance<KaEnumEntrySymbol>()
                    .distinctBy { it.name?.asString() }
                    .map { CirEnumCase(name = it.name?.asString() ?: "_", rawValue = null) }
                    .toList(),
            )
            KaClassKind.INTERFACE -> CirClass(
                name = name,
                visibility = vis,
                documentation = doc,
                isInterface = true,
                typeParameters = typeParameters.map { it.toCirTypeParameter() },
                declarations = declaredMembers().toMutableList(),
            )
            KaClassKind.OBJECT -> CirClass(
                name = name,
                visibility = vis,
                documentation = doc,
                isObject = true,
                typeParameters = typeParameters.map { it.toCirTypeParameter() },
                declarations = declaredMembers().toMutableList(),
            )
            else -> {
                val superRefs = superTypes.map { it.toCirType() }
                val superClass = superRefs.filterIsInstance<CirNominalType>()
                    .firstOrNull { it.fqName != "kotlin.Any" }
                val interfaces = superRefs.filterIsInstance<CirNominalType>()
                    .filter { it.fqName != "kotlin.Any" && it.fqName != superClass?.fqName }
                CirClass(
                    name = name,
                    visibility = vis,
                    documentation = doc,
                    isData = isData,
                    isAbstract = modality == KaSymbolModality.ABSTRACT,
                    isSealed = modality == KaSymbolModality.SEALED,
                    superClass = superClass,
                    interfaces = interfaces,
                    typeParameters = typeParameters.map { it.toCirTypeParameter() },
                    constructorParameters = primaryConstructorParameters(),
                    declarations = declaredMembers().toMutableList(),
                )
            }
        }
    }

    context(session: KaSession)
    private fun KaTypeAliasSymbol.toCirTypealias(): CirTypealias? {
        if (visibility != KaSymbolVisibility.PUBLIC) return null
        return CirTypealias(
            name = name?.asString() ?: "Unnamed",
            visibility = visibility.toCirVisibility(),
            target = expandedType.toCirType(),
        )
    }

    context(session: KaSession)
    private fun KaNamedClassSymbol.primaryConstructorParameters(): List<CirParameter> {
        val primary = this.declaredMemberScope.constructors
            .firstOrNull { it.isPrimary } ?: return emptyList()
        return primary.valueParameters.map { it.toCirParameter() }
    }

    // ------------------------------------------------------------------ members

    context(session: KaSession)
    private fun KaNamedClassSymbol.declaredMembers(): List<CirDeclaration> {
        val members = mutableListOf<CirDeclaration>()
        for (callable in this.declaredMemberScope.callables) {
            if (callable.origin != KaSymbolOrigin.SOURCE && callable.origin != KaSymbolOrigin.LIBRARY) continue
            if (callable.visibility != KaSymbolVisibility.PUBLIC) continue
            when (callable) {
                is KaFunctionSymbol -> members += callable.toCirFunction()
                is KaPropertySymbol -> members += callable.toCirProperty()
                else -> {}
            }
        }
        return members
    }

    context(session: KaSession)
    private fun KaCallableSymbol.toCirTopLevelDeclaration(): CirDeclaration? = when (this) {
        is KaFunctionSymbol -> toCirFunction()
        is KaPropertySymbol -> toCirProperty()
        else -> null
    }

    context(session: KaSession)
    private fun KaFunctionSymbol.toCirFunction() = CirFunction(
        name = name?.asString() ?: "_",
        visibility = visibility.toCirVisibility(),
        documentation = getKDocString(),
        isStatic = false,
        isInstance = true,
        isSuspend = isSuspend,
        isOperator = false,
        isOverride = false,
        receiverType = receiverType?.toCirType(),
        typeParameters = typeParameters.map { it.toCirTypeParameter() },
        parameters = valueParameters.map { it.toCirParameter() },
        returnType = returnType.toCirType(),
    )

    context(session: KaSession)
    private fun KaPropertySymbol.toCirProperty() = CirProperty(
        name = name?.asString() ?: "_",
        visibility = visibility.toCirVisibility(),
        documentation = getKDocString(),
        type = returnType.toCirType(),
        isReadOnly = !hasSetter,
        isStatic = isStatic,
        isInstance = !isStatic,
        isOverride = isOverride,
        hasGetter = hasGetter,
        hasSetter = hasSetter,
    )

    context(session: KaSession)
    private fun KaValueParameterSymbol.toCirParameter() = CirParameter(
        name = name?.asString() ?: "_",
        type = returnType.toCirType(),
        hasDefault = hasDefaultValue,
        isVararg = isVararg,
    )

    context(session: KaSession)
    private fun KaTypeParameterSymbol.toCirTypeParameter() = CirTypeParameter(
        name = name?.asString() ?: "T",
        variance = variance.toCirVariance(),
        upperBound = upperBounds.firstOrNull { !it.isAnyType() }?.toCirType(),
    )

    // ------------------------------------------------------------------ types

    context(session: KaSession)
    private fun KaType.toCirType(): CirType {
        val nullable = nullability == KaTypeNullability.NULLABLE
        val base = when (this) {
            is KaFunctionType -> CirFunctionType(
                parameterTypes = parameterTypes.map { it.toCirType() },
                returnType = returnType.toCirType(),
                isSuspend = isSuspend,
            )
            is KaClassType -> {
                val args = typeArguments.map { it.toCirTypeArgument() }
                builtinOrNominal(classId, args)
            }
            is KaTypeParameterType -> CirTypeParameterType(name = symbol.name?.asString() ?: "T")
            is KaFlexibleType -> upperBound.toCirType()
            is KaDefinitelyNotNullType -> original.toCirType().toNonNullable()
            is KaDynamicType -> CirBuiltinType(CirBuiltinKind.ANY, nullable = true)
            is KaErrorType -> CirBuiltinType(CirBuiltinKind.ERROR, nullable = true)
            is KaCapturedType -> CirBuiltinType(CirBuiltinKind.ANY)
            is KaIntersectionType -> CirBuiltinType(CirBuiltinKind.ANY)
            else -> CirBuiltinType(CirBuiltinKind.ANY)
        }
        return when (base) {
            is CirBuiltinType -> if (nullable && base.kind != CirBuiltinKind.UNIT) CirBuiltinType(base.kind, nullable = true) else base
            is CirNominalType -> if (nullable && base.fqName != "kotlin.Nothing") CirNominalType(base.fqName, base.simpleName, base.typeArguments, nullable = true) else base
            is CirFunctionType -> if (nullable) CirFunctionType(base.parameterTypes, base.returnType, base.isSuspend, nullable = true) else base
            else -> base
        }
    }

    private fun CirType.toNonNullable(): CirType = when (this) {
        is CirBuiltinType -> CirBuiltinType(kind, nullable = false)
        is CirNominalType -> CirNominalType(fqName, simpleName, typeArguments, nullable = false)
        is CirFunctionType -> CirFunctionType(parameterTypes, returnType, isSuspend, nullable = false)
        else -> this
    }

    context(session: KaSession)
    private fun KaTypeProjection.toCirTypeArgument(): CirType {
        return if (this is KaStarTypeProjection) CirStarProjection()
        else (this as KaTypeProjection).type?.toCirType() ?: CirStarProjection()
    }

    private fun builtinOrNominal(classId: org.jetbrains.kotlin.name.ClassId?, typeArgs: List<CirType>): CirType {
        val fqn = classId?.asSingleFqName()?.asString()
        val kind = BUILTINS[fqn]
        return if (kind != null) {
            CirBuiltinType(kind)
        } else {
            CirNominalType(
                fqName = fqn ?: "<unknown>",
                simpleName = classId?.relativeClassName?.asString() ?: fqn ?: "Unknown",
                typeArguments = typeArgs,
            )
        }
    }

    context(session: KaSession)
    private fun KaType.isAnyType(): Boolean =
        this is KaClassType && classId?.asSingleFqName()?.asString() == "kotlin.Any"

    private fun Variance.toCirVariance(): CirVariance = when (this) {
        Variance.OUT_VARIANCE -> CirVariance.OUT
        Variance.IN_VARIANCE -> CirVariance.IN
        Variance.INVARIANT -> CirVariance.INVARIANT
    }

    private fun KaSymbolVisibility.toCirVisibility(): CirVisibility = when (this) {
        KaSymbolVisibility.PUBLIC -> CirVisibility.PUBLIC
        KaSymbolVisibility.PROTECTED -> CirVisibility.PROTECTED
        KaSymbolVisibility.INTERNAL -> CirVisibility.INTERNAL
        else -> CirVisibility.PRIVATE
    }

    private companion object {
        val BUILTINS = mapOf(
            "kotlin.Int" to CirBuiltinKind.INT,
            "kotlin.Long" to CirBuiltinKind.LONG,
            "kotlin.Short" to CirBuiltinKind.SHORT,
            "kotlin.Byte" to CirBuiltinKind.BYTE,
            "kotlin.Float" to CirBuiltinKind.FLOAT,
            "kotlin.Double" to CirBuiltinKind.DOUBLE,
            "kotlin.Boolean" to CirBuiltinKind.BOOLEAN,
            "kotlin.Char" to CirBuiltinKind.CHAR,
            "kotlin.UInt" to CirBuiltinKind.UINT,
            "kotlin.ULong" to CirBuiltinKind.ULONG,
            "kotlin.UShort" to CirBuiltinKind.USHORT,
            "kotlin.UByte" to CirBuiltinKind.UBYTE,
            "kotlin.String" to CirBuiltinKind.STRING,
            "kotlin.Unit" to CirBuiltinKind.UNIT,
            "kotlin.Any" to CirBuiltinKind.ANY,
            "kotlin.Nothing" to CirBuiltinKind.NOTHING,
            "kotlin.IntArray" to CirBuiltinKind.INT_ARRAY,
            "kotlin.LongArray" to CirBuiltinKind.LONG_ARRAY,
            "kotlin.ShortArray" to CirBuiltinKind.SHORT_ARRAY,
            "kotlin.ByteArray" to CirBuiltinKind.BYTE_ARRAY,
            "kotlin.FloatArray" to CirBuiltinKind.FLOAT_ARRAY,
            "kotlin.DoubleArray" to CirBuiltinKind.DOUBLE_ARRAY,
            "kotlin.BooleanArray" to CirBuiltinKind.BOOLEAN_ARRAY,
            "kotlin.CharArray" to CirBuiltinKind.CHAR_ARRAY,
        )
    }
}
