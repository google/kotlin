/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.konan.diagnostics

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.utils.addToStdlib.cast

object NativeObjCNameChecker : DeclarationChecker {
    private val objCNameFqName = FqName("kotlin.native.ObjCName")

    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        checkDeclaration(declaration, descriptor, context)
        checkOverrides(declaration, descriptor, context)
    }

    private fun checkDeclaration(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        val objCNames = descriptor.getObjCNames().filterNotNull()
        if (objCNames.isEmpty()) return
        if (descriptor is CallableMemberDescriptor && descriptor.overriddenDescriptors.isNotEmpty()) {
            objCNames.forEach {
                val reportLocation = DescriptorToSourceUtils.getSourceFromAnnotation(it.annotation) ?: declaration
                context.trace.report(ErrorsNative.INAPPLICABLE_OBJC_NAME.on(reportLocation))
            }
        }
        objCNames.forEach { checkObjCName(it, declaration, context) }
    }

    private fun checkObjCName(objCName: ObjCName, declaration: KtDeclaration, context: DeclarationCheckerContext) {
        if (objCName.name == null && objCName.swiftName == null) {
            val reportLocation = DescriptorToSourceUtils.getSourceFromAnnotation(objCName.annotation) ?: declaration
            context.trace.report(ErrorsNative.INVALID_OBJC_NAME.on(reportLocation))
        }
        val invalidNameChars = objCName.name?.toSet()?.intersect(NativeIdentifierChecker.invalidChars) ?: emptySet()
        val invalidSwiftNameChars = objCName.swiftName?.toSet()?.intersect(NativeIdentifierChecker.invalidChars) ?: emptySet()
        val invalidChars = invalidNameChars + invalidSwiftNameChars
        if (invalidChars.isNotEmpty()) {
            val reportLocation = DescriptorToSourceUtils.getSourceFromAnnotation(objCName.annotation) ?: declaration
            context.trace.report(ErrorsNative.INVALID_CHARACTERS_OBJC_NAME.on(reportLocation, invalidChars.joinToString("")))
        }
    }

    private fun checkOverrides(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (descriptor !is CallableMemberDescriptor || descriptor.overriddenDescriptors.isEmpty()) return
        val objCNames = descriptor.overriddenDescriptors.map { it.getFirstBaseDescriptor().getObjCNames() }
        if (!objCNames.allNamesEquals()) {
            context.trace.report(ErrorsNative.INCOMPATIBLE_OBJC_NAME_OVERRIDE.on(declaration))
        }
    }

    private fun CallableMemberDescriptor.getFirstBaseDescriptor(): CallableMemberDescriptor =
        if (overriddenDescriptors.isEmpty()) this else overriddenDescriptors.first().getFirstBaseDescriptor()

    private class ObjCName(
        val annotation: AnnotationDescriptor
    ) {
        val name: String? = annotation.argumentValue("name")?.value?.cast<String>()?.takeIf { it.isNotBlank() }
        val swiftName: String? = annotation.argumentValue("swiftName")?.value?.cast<String>()?.takeIf { it.isNotBlank() }
    }

    private fun DeclarationDescriptor.getObjCName(): ObjCName? = annotations.findAnnotation(objCNameFqName)?.let(::ObjCName)

    private fun DeclarationDescriptor.getObjCNames(): List<ObjCName?> = when (this) {
        is FunctionDescriptor -> buildList {
            add(getObjCName())
            add(extensionReceiverParameter?.getObjCName())
            valueParameters.forEach { add(it.getObjCName()) }
        }
        else -> listOf(getObjCName())
    }

    private fun ObjCName?.nameEquals(other: ObjCName?): Boolean = this?.name == other?.name && this?.swiftName == other?.swiftName

    private fun List<List<ObjCName?>>.allNamesEquals(): Boolean {
        val first = this[0]
        for (i in 1 until size) {
            val current = this[i]
            if (first.size != current.size) return false
            for (ii in first.indices) {
                if (!first[ii].nameEquals(current[ii])) return false
            }
        }
        return true
    }
}