/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis

import com.intellij.lang.LighterASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.fir.*

fun FirSourceElement.getChild(type: IElementType, index: Int = 0, depth: Int = -1, reverse: Boolean = false): FirSourceElement? {
    return getChild(setOf(type), index, depth, reverse)
}

fun FirSourceElement.getChild(types: TokenSet, index: Int = 0, depth: Int = -1, reverse: Boolean = false): FirSourceElement? {
    return getChild(types.types.toSet(), index, depth, reverse)
}

fun FirSourceElement.getChild(types: Set<IElementType>, index: Int = 0, depth: Int = -1, reverse: Boolean = false): FirSourceElement? {
    return when (this) {
        is FirPsiSourceElement -> {
            getChild(types, index, depth, reverse)
        }
        is FirLightSourceElement -> {
            getChild(types, index, depth, reverse)
        }
        else -> null
    }
}

private fun FirPsiSourceElement.getChild(types: Set<IElementType>, index: Int, depth: Int, reverse: Boolean): FirSourceElement? {
    val visitor = PsiElementFinderByType(types, index, depth, reverse)
    return visitor.find(psi)?.toFirPsiSourceElement()
}

private fun FirLightSourceElement.getChild(types: Set<IElementType>, index: Int, depth: Int, reverse: Boolean): FirSourceElement? {
    val visitor = LighterTreeElementFinderByType(treeStructure, types, index, depth, reverse)
    val childNode = visitor.find(lighterASTNode) ?: return null
    return buildChildSourceElement(childNode)
}

/**
 * Keeps 'padding' of parent node in child node
 */
internal fun FirLightSourceElement.buildChildSourceElement(childNode: LighterASTNode): FirLightSourceElement {
    val offsetDelta = startOffset - lighterASTNode.startOffset
    return childNode.toFirLightSourceElement(
        treeStructure,
        startOffset = childNode.startOffset + offsetDelta,
        endOffset = childNode.endOffset + offsetDelta
    )
}

