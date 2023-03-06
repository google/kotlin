/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.plugin.directives

import org.jetbrains.kotlin.test.directives.model.DirectiveApplicability
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

object PluginPrototypeDirectives : SimpleDirectivesContainer() {
    val DISABLE_PLUGINS by directive(
        description = "Disable plugin phases on a module",
        applicability = DirectiveApplicability.Module
    )
}
