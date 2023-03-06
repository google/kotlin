/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.plugin.services

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar.ExtensionStorage
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.plugin.FirPluginPrototypeExtensionRegistrar
import org.jetbrains.kotlin.fir.plugin.directives.PluginPrototypeDirectives
import org.jetbrains.kotlin.ir.plugin.GeneratedDeclarationsIrBodyFiller
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

class ExtensionRegistrarConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(PluginPrototypeDirectives)

    override fun ExtensionStorage.registerCompilerExtensions(module: TestModule, configuration: CompilerConfiguration) {
        if (!module.directives.contains(PluginPrototypeDirectives.DISABLE_PLUGINS)) {
            FirExtensionRegistrarAdapter.registerExtension(FirPluginPrototypeExtensionRegistrar())
            IrGenerationExtension.registerExtension(GeneratedDeclarationsIrBodyFiller())
        }
    }
}
