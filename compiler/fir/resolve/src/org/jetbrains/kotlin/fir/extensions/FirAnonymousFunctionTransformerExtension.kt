/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.extensions

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.constructFunctionalType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import kotlin.reflect.KClass

abstract class FirAnonymousFunctionTransformerExtension(session: FirSession) : FirExtension(session) {
    companion object {
        val NAME = FirExtensionPointName("AnonymousFunctionTransformerExtension")
    }

    final override val name: FirExtensionPointName
        get() = NAME

    final override val extensionType: KClass<out FirExtension> = FirAnonymousFunctionTransformerExtension::class

    // Called when resolving the declaration for an anonymous function with an expected type.
    // Adds all inferred annotations to the argument `function`.
    abstract fun getInferredAnnotationsForAnonymousFunction(
        anonymousFunction: FirAnonymousFunction,
        expectedType: ConeKotlinType
    ): List<FirAnnotation>

    // Precondition: All inferred annotations have been added to the `function` argument.
    // Implementations should return null to fall back to the default implementation.
    abstract fun getCustomFunctionalTypeForAnonymousFunction(
        function: FirAnonymousFunction,
        isSuspend: Boolean
    ): ConeKotlinType?

    fun interface Factory : FirExtension.Factory<FirAnonymousFunctionTransformerExtension>
}

val FirExtensionService.anonymousFunctionTransformerExtensions: List<FirAnonymousFunctionTransformerExtension> by FirExtensionService.registeredExtensions()

fun FirAnonymousFunction.constructFunctionalTypeRefWithExtensions(
    extensions: List<FirAnonymousFunctionTransformerExtension>,
    isSuspend: Boolean
): FirResolvedTypeRef {
    val functionalType = extensions.firstNotNullOfOrNull {
        it.getCustomFunctionalTypeForAnonymousFunction(this@constructFunctionalTypeRefWithExtensions, isSuspend)
    } ?: constructFunctionalType(isSuspend)

    return buildResolvedTypeRef {
        source = this@constructFunctionalTypeRefWithExtensions.source?.fakeElement(KtFakeSourceElementKind.ImplicitTypeRef)
        type = functionalType
    }
}
