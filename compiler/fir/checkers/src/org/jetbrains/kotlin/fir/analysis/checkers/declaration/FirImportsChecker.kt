/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.analysis.checkers.collectEnumEntries
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirDeprecationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.fullyExpandedClass
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.FirErrorImport
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirImport
import org.jetbrains.kotlin.fir.declarations.FirResolvedImport
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.declarations.utils.isOperator
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.expressions.OperatorConventions

object FirImportsChecker : FirFileChecker() {
    override fun check(declaration: FirFile, context: CheckerContext, reporter: DiagnosticReporter) {
        declaration.imports.forEach { import ->
            if (import is FirErrorImport) return@forEach
            if (import.isAllUnder && import !is FirResolvedImport) {
                checkAllUnderFromEnumEntry(import, context, reporter)
            }
            if (!import.isAllUnder) {
                checkCanBeImported(import, context, reporter)
                if (import is FirResolvedImport) {
                    checkOperatorRename(import, context, reporter)
                }
            }
            checkDeprecatedImport(import, context, reporter)
        }
        checkConflictingImports(declaration.imports, context, reporter)
    }

    private fun checkAllUnderFromEnumEntry(import: FirImport, context: CheckerContext, reporter: DiagnosticReporter) {
        val fqName = import.importedFqName ?: return
        if (fqName.isRoot || fqName.parent().isRoot) return
        val classId = ClassId.topLevel(fqName.parent())
        val classSymbol = classId.resolveToClass(context) ?: return
        if (classSymbol.isEnumClass && classSymbol.collectEnumEntries().any { it.callableId.callableName == fqName.shortName() }) {
            reporter.reportOn(import.source, FirErrors.CANNOT_ALL_UNDER_IMPORT_FROM_SINGLETON, classSymbol.classId.shortClassName, context)
        }
    }

    private fun checkCanBeImported(import: FirImport, context: CheckerContext, reporter: DiagnosticReporter) {
        val importedFqName = import.importedFqName ?: return
        val importedName = importedFqName.shortName()
        //empty name come from LT in some erroneous cases
        if (importedName.isSpecial || importedName.identifier.isEmpty()) return

        val symbolProvider = context.session.symbolProvider
        val parentClassId = (import as? FirResolvedImport)?.resolvedParentClassId
        if (parentClassId != null) {
            val classId = parentClassId.createNestedClassId(importedName)
            if (symbolProvider.getClassLikeSymbolByClassId(classId) != null) return

            val parentClassSymbol = parentClassId.resolveToClass(context) ?: return

            when (parentClassSymbol.canBeImported(context, importedName)) {
                ImportStatus.MISSING -> reporter.reportOn(
                    import.source,
                    FirErrors.UNRESOLVED_IMPORT,
                    importedName.asString(),
                    context,
                )
                ImportStatus.CAN_NOT_BE_IMPORTED -> reporter.reportOn(import.source, FirErrors.CANNOT_BE_IMPORTED, importedName, context)
                else -> {}
            }
        } else {
            val importedClassId = ClassId.topLevel(importedFqName)
            val resolvedToClass = importedClassId.resolveToClass(context) != null
            val resolvedToSymbols = symbolProvider.getTopLevelCallableSymbols(importedFqName.parent(), importedName).isNotEmpty()
            val resolvedToPackages = symbolProvider.getPackage(importedFqName) != null
            when {
                resolvedToClass || resolvedToSymbols -> return
                resolvedToPackages -> reporter.reportOn(import.source, FirErrors.PACKAGE_CANNOT_BE_IMPORTED, context)
                else -> reporter.reportOn(
                    import.source,
                    FirErrors.UNRESOLVED_IMPORT,
                    importedName.asString(),
                    context,
                )
            }
        }
    }

    private fun checkConflictingImports(imports: List<FirImport>, context: CheckerContext, reporter: DiagnosticReporter) {
        val interestingImports = imports
            .filterIsInstance<FirResolvedImport>()
            .filter { import ->
                !import.isAllUnder
                        && import.importedName?.identifierOrNullIfSpecial?.isNotEmpty() == true
                        && import.resolvesToClass(context)
            }
        interestingImports
            .groupBy { it.aliasName ?: it.importedName!! }
            .values
            .filter { it.size > 1 }
            .forEach { conflicts ->
                conflicts.forEach {
                    reporter.reportOn(it.source, FirErrors.CONFLICTING_IMPORT, it.importedName!!, context)
                }
            }
    }

    private fun checkOperatorRename(import: FirResolvedImport, context: CheckerContext, reporter: DiagnosticReporter) {
        val alias = import.aliasName ?: return
        val importedName = import.importedName ?: return
        if (!OperatorConventions.isConventionName(alias)) return
        val classId = import.resolvedParentClassId
        val illegalRename = if (classId != null) {
            val classFir = classId.resolveToClass(context) ?: return
            classFir.classKind.isSingleton && classFir.hasFunction(context, importedName) { it.isOperator }
        } else {
            context.session.symbolProvider.getTopLevelFunctionSymbols(import.packageFqName, importedName).any {
                it.isOperator
            }
        }
        if (illegalRename) {
            reporter.reportOn(import.source, FirErrors.OPERATOR_RENAMED_ON_IMPORT, context)
        }
    }

    private fun FirResolvedImport.resolvesToClass(context: CheckerContext): Boolean {
        if (resolvedParentClassId != null) {
            if (isAllUnder) return true
            val parentClass = resolvedParentClassId!!
            val relativeClassName = this.relativeParentClassName ?: return false
            val importedName = this.importedName ?: return false
            val innerClassId = ClassId(parentClass.packageFqName, relativeClassName.child(importedName), false)
            return innerClassId.resolveToClass(context) != null
        } else {
            val importedFqName = importedFqName ?: return false
            if (importedFqName.isRoot) return false
            val importedClassId = ClassId.topLevel(importedFqName)
            return importedClassId.resolveToClass(context) != null
        }
    }

    private fun ClassId.resolveToClass(context: CheckerContext): FirRegularClassSymbol? {
        val classSymbol = context.session.symbolProvider.getClassLikeSymbolByClassId(this) ?: return null
        return when (classSymbol) {
            is FirRegularClassSymbol -> classSymbol
            is FirTypeAliasSymbol -> classSymbol.fullyExpandedClass(context.session)
            else -> null
        }
    }

    private fun FirRegularClassSymbol.hasFunction(
        context: CheckerContext,
        name: Name,
        predicate: (FirNamedFunctionSymbol) -> Boolean
    ): Boolean {
        var result = false
        context.session.declaredMemberScope(this).processFunctionsByName(name) { sym ->
            if (!result) {
                result = predicate(sym)
            }
        }
        return result
    }

    private enum class ImportStatus {
        OK,
        CAN_NOT_BE_IMPORTED,
        MISSING
    }

    @OptIn(SymbolInternals::class)
    private fun FirRegularClassSymbol.canBeImported(context: CheckerContext, name: Name): ImportStatus {
        if (classKind == ClassKind.ENUM_CLASS && declarationSymbols.firstOrNull { it is FirEnumEntrySymbol && it.name == name } != null) {
            return ImportStatus.OK
        }
        if (!unsubstitutedScope(context).getCallableNames().contains(name)) return ImportStatus.MISSING
        if (classKind.isSingleton) return ImportStatus.OK

        val scope = context.session.declaredMemberScope(this)
        var hasStatic = false
        var hasIllegal = false
        scope.processFunctionsByName(name) { sym ->
            if (sym.isStatic) hasStatic = true
            else hasIllegal = true
        }
        if (hasStatic) return ImportStatus.OK
        if (hasIllegal) return ImportStatus.CAN_NOT_BE_IMPORTED

        scope.processPropertiesByName(name) { sym ->
            if (sym.isStatic) hasStatic = true
            else hasIllegal = true
        }

        return if (hasStatic || !hasIllegal) {
            ImportStatus.OK
        } else {
            ImportStatus.CAN_NOT_BE_IMPORTED
        }
    }

    private fun checkDeprecatedImport(import: FirImport, context: CheckerContext, reporter: DiagnosticReporter) {
        val importedFqName = import.importedFqName ?: return
        if (importedFqName.isRoot || importedFqName.shortName().asString().isEmpty()) return
        val classId = (import as? FirResolvedImport)?.resolvedParentClassId ?: ClassId.topLevel(importedFqName)
        val classLike: FirRegularClassSymbol = classId.resolveToClass(context) ?: return
        FirDeprecationChecker.reportDeprecationIfNeeded(import.source, classLike, null, context, reporter)
    }
}
