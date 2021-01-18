/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks

import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class CompilerPluginOptions {
    val subpluginOptionsByPluginId = mutableMapOf<String, MutableList<SubpluginOption>>()

    val arguments: List<String>
        get() = subpluginOptionsByPluginId.flatMap { (pluginId, subplubinOptions) ->
            subplubinOptions.map { option ->
                "plugin:$pluginId:${option.key}=${option.value}"
            }
        }

    fun addPluginArgument(pluginId: String, option: SubpluginOption) {
        subpluginOptionsByPluginId.getOrPut(pluginId) { mutableListOf() }.add(option)
    }

    operator fun plus(options: CompilerPluginOptions?): CompilerPluginOptions {
        if (options == null) return this
        val newOptions = CompilerPluginOptions()
        newOptions.subpluginOptionsByPluginId += subpluginOptionsByPluginId
        newOptions.subpluginOptionsByPluginId += options.subpluginOptionsByPluginId

        return newOptions
    }
}