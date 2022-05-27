/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:OptIn(ExperimentalObjCName::class)

package objCNameB

// https://youtrack.jetbrains.com/issue/KT-50767
@ObjCName("ObjCNameC1B")
class ObjCNameC1 {
    fun foo(): String = "b"
}

@ObjCName("MyObjCArray", "MySwiftArray")
class MyKotlinArray {
    // https://developer.apple.com/documentation/foundation/nsarray/1409982-count
    @ObjCName("count")
    val size: Int = 0
    // https://developer.apple.com/documentation/foundation/nsarray/1417076-index
    @ObjCName(swiftName = "index")
    fun indexOf(@ObjCName("object", "of") element: Int): Int = element
}

interface ObjCNameI1 {
    @ObjCName("someOtherValue")
    val someValue: Int
}

class ObjCNameC2: ObjCNameI1 {
    override var someValue: Int = 0
}
