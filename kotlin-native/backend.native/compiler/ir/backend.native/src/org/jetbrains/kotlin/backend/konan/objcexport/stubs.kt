/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.backend.konan.objcexport

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.source.PsiSourceElement

class ObjCComment(val contentLines: List<String>) {
    constructor(vararg contentLines: String) : this(contentLines.toList())
}

data class ObjCClassForwardDeclaration(
        val className: String,
        val typeDeclarations: List<ObjCGenericTypeDeclaration> = emptyList()
)

abstract class Stub<out D : DeclarationDescriptor>(val comment: ObjCComment? = null) {
    abstract val descriptor: D?
    abstract val name: String
    open val psi: PsiElement?
        get() = ((descriptor as? DeclarationDescriptorWithSource)?.source as? PsiSourceElement)?.psi
    open val isValid: Boolean
        get() = descriptor?.module?.isValid ?: true
}

abstract class ObjCTopLevel<out D : DeclarationDescriptor>() : Stub<D>()

abstract class ObjCClass<out D : DeclarationDescriptor>() : ObjCTopLevel<D>() {
    abstract val attributes: List<String>
    abstract val superProtocols: List<String>
    abstract val members: List<Stub<*>>
}

abstract class ObjCProtocol() : ObjCClass<ClassDescriptor>()

class ObjCProtocolImpl(
        override val name: String,
        override val descriptor: ClassDescriptor,
        override val superProtocols: List<String>,
        override val members: List<Stub<*>>,
        override val attributes: List<String> = emptyList()) : ObjCProtocol()

abstract class ObjCInterface(val generics: List<ObjCGenericTypeDeclaration>,
                             val categoryName: String?) : ObjCClass<ClassDescriptor>() {
    abstract val superClass: String?
    abstract val superClassGenerics: List<ObjCNonNullReferenceType>
}

class ObjCInterfaceImpl(
        override val name: String,
        generics: List<ObjCGenericTypeDeclaration> = emptyList(),
        override val descriptor: ClassDescriptor? = null,
        override val superClass: String? = null,
        override val superClassGenerics: List<ObjCNonNullReferenceType> = emptyList(),
        override val superProtocols: List<String> = emptyList(),
        categoryName: String? = null,
        override val members: List<Stub<*>> = emptyList(),
        override val attributes: List<String> = emptyList()
) : ObjCInterface(generics, categoryName)

class ObjCMethod(
        override val descriptor: DeclarationDescriptor?,
        val isInstanceMethod: Boolean,
        val returnType: ObjCType,
        val selectors: List<String>,
        val parameters: List<ObjCParameter>,
        val attributes: List<String>,
        comment: ObjCComment? = null,
        override val name: String = buildMethodName(selectors, parameters)
) : Stub<DeclarationDescriptor>(comment)

class ObjCParameter(override val name: String,
                    override val descriptor: ParameterDescriptor?,
                    val type: ObjCType) : Stub<ParameterDescriptor>()

class ObjCProperty(override val name: String,
                   override val descriptor: DeclarationDescriptorWithSource?,
                   val type: ObjCType,
                   val propertyAttributes: List<String>,
                   val setterName: String? = null,
                   val getterName: String? = null,
                   val declarationAttributes: List<String> = emptyList()) : Stub<DeclarationDescriptorWithSource>() {

    @Deprecated("", ReplaceWith("this.propertyAttributes"), DeprecationLevel.WARNING)
    val attributes: List<String> get() = propertyAttributes
}

private fun buildMethodName(selectors: List<String>, parameters: List<ObjCParameter>): String =
        if (selectors.size == 1 && parameters.size == 0) {
            selectors[0]
        } else {
            assert(selectors.size == parameters.size)
            selectors.joinToString(separator = "")
        }
