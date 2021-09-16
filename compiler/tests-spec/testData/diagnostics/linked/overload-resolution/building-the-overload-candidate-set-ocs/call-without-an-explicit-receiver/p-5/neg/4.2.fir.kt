// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_VARIABLE -ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE -UNUSED_VALUE -UNUSED_PARAMETER -UNUSED_EXPRESSION
// SKIP_TXT



// FILE: TestCase1.kt
// TESTCASE NUMBER: 3

package tests.case1

import lib.case1.*

interface I
//class A : I
class B : I

fun case1(){
    <!DEBUG_INFO_EXPRESSION_TYPE("lib.case1.A")!><!INVISIBLE_REFERENCE!>A<!>()<!>
    <!DEBUG_INFO_CALL("fqName: fqName is unknown; typeCall: unresolved")!><!INVISIBLE_REFERENCE!>A<!>()<!>
}

// FILE: Lib.kt
package lib.case1

//fun A() : String = ""

object A {
    /*operator*/ fun invoke() : Int = 1
}
