// FILE: kotlin.kt
package kotlin.native

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION
)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class ObjCName(val name: String = "", val swiftName: String = "", val exact: Boolean = false)

// FILE: test.kt
@ObjCName("ObjCClass", "SwiftClass")
open class KotlinClass {
    @ObjCName("objCProperty")
    open var kotlinProperty: Int = 0
    @ObjCName(swiftName = "swiftFunction")
    open fun @receiver:ObjCName("objCReceiver") Int.kotlinFunction(
        @ObjCName("objCParam") kotlinParam: Int
    ): Int = this + kotlinParam
}

@ObjCName("ObjCSubClass", "SwiftSubClass")
class KotlinSubClass: KotlinClass() {
    <!INAPPLICABLE_OBJC_NAME!>@ObjCName("objCProperty")<!>
    override var kotlinProperty: Int = 1
    <!INAPPLICABLE_OBJC_NAME!>@ObjCName(swiftName = "swiftFunction")<!>
    override fun <!INAPPLICABLE_OBJC_NAME!>@receiver:ObjCName("objCReceiver")<!> Int.kotlinFunction(
        <!INAPPLICABLE_OBJC_NAME!>@ObjCName("objCParam")<!> kotlinParam: Int
    ): Int = this + kotlinParam * 2
}

<!INVALID_OBJC_NAME!>@ObjCName()<!>
val invalidObjCName: Int = 0

<!INVALID_CHARACTERS_OBJC_NAME!>@ObjCName("validName", "invalid.name")<!>
val invalidCharactersObjCNameA: Int = 0

<!INVALID_CHARACTERS_OBJC_NAME!>@ObjCName("invalid.name", "validName")<!>
val invalidCharactersObjCNameB: Int = 0

interface KotlinInterfaceA {
    @ObjCName("objCPropertyA", "swiftPropertyA")
    var kotlinPropertyA: Int
    @ObjCName("objCPropertyB", "swiftPropertyB")
    var kotlinPropertyB: Int
    @ObjCName("objCPropertyB")
    var kotlinPropertyC: Int
    @ObjCName(swiftName ="swiftPropertyB")
    var kotlinPropertyD: Int
    var kotlinPropertyE: Int
    var kotlinPropertyF: Int

    @ObjCName("objCFunctionA", "swiftFunctionA")
    fun @receiver:ObjCName("objCReceiver", "swiftReceiver") Int.kotlinFunctionA(
        @ObjCName("objCParam", "swiftParam") kotlinParam: Int
    ): Int
    @ObjCName("objCFunctionB", "swiftFunctionB")
    fun @receiver:ObjCName("objCReceiver", "swiftReceiver") Int.kotlinFunctionB(
        @ObjCName("objCParam", "swiftParam") kotlinParam: Int
    ): Int
    @ObjCName("objCFunctionC", "swiftFunctionC")
    fun @receiver:ObjCName("objCReceiver", "swiftReceiver") Int.kotlinFunctionC(
        @ObjCName("objCParam", "swiftParam") kotlinParam: Int
    ): Int
    @ObjCName("objCFunctionD", "swiftFunctionD")
    fun @receiver:ObjCName("objCReceiver", "swiftReceiver") Int.kotlinFunctionD(
        @ObjCName("objCParam", "swiftParam") kotlinParam: Int
    ): Int
    @ObjCName("objCFunctionE", "swiftFunctionE")
    fun Int.kotlinFunctionE(@ObjCName("objCParam", "swiftParam") kotlinParam: Int): Int
}

interface KotlinInterfaceB {
    @ObjCName("objCPropertyA", "swiftPropertyA")
    var kotlinPropertyA: Int
    @ObjCName("objCPropertyBB", "swiftPropertyB")
    var kotlinPropertyB: Int
    @ObjCName(swiftName ="swiftPropertyC")
    var kotlinPropertyC: Int
    @ObjCName("objCPropertyD")
    var kotlinPropertyD: Int
    @ObjCName("objCPropertyE")
    var kotlinPropertyE: Int
    var kotlinPropertyF: Int

    @ObjCName("objCFunctionA", "swiftFunctionA")
    fun @receiver:ObjCName("objCReceiver", "swiftReceiver") Int.kotlinFunctionA(
        @ObjCName("objCParam", "swiftParam") kotlinParam: Int
    ): Int
    @ObjCName("objCFunctionBB", "swiftFunctionB")
    fun @receiver:ObjCName("objCReceiver", "swiftReceiver") Int.kotlinFunctionB(
        @ObjCName("objCParam", "swiftParam") kotlinParam: Int
    ): Int
    @ObjCName("objCFunctionC", "swiftFunctionC")
    fun @receiver:ObjCName("objCReceiverC", "swiftReceiver") Int.kotlinFunctionC(
        @ObjCName("objCParam", "swiftParam") kotlinParam: Int
    ): Int
    @ObjCName("objCFunctionD", "swiftFunctionD")
    fun @receiver:ObjCName("objCReceiver", "swiftReceiver") Int.kotlinFunctionD(
        @ObjCName("objCParamD", "swiftParam") kotlinParam: Int
    ): Int
    fun @receiver:ObjCName("objCFunctionE", "swiftFunctionE") Int.kotlinFunctionE(
        @ObjCName("objCParam", "swiftParam") kotlinParam: Int
    ): Int
}

class KotlinOverrideClass: KotlinInterfaceA, KotlinInterfaceB {
    override var kotlinPropertyA: Int = 0
    <!INCOMPATIBLE_OBJC_NAME_OVERRIDE!>override var kotlinPropertyB: Int = 0<!>
    <!INCOMPATIBLE_OBJC_NAME_OVERRIDE!>override var kotlinPropertyC: Int = 0<!>
    <!INCOMPATIBLE_OBJC_NAME_OVERRIDE!>override var kotlinPropertyD: Int = 0<!>
    <!INCOMPATIBLE_OBJC_NAME_OVERRIDE!>override var kotlinPropertyE: Int = 0<!>
    override var kotlinPropertyF: Int = 0

    override fun Int.kotlinFunctionA(kotlinParam: Int): Int = this + kotlinParam
    <!INCOMPATIBLE_OBJC_NAME_OVERRIDE!>override fun Int.kotlinFunctionB(kotlinParam: Int): Int = this + kotlinParam<!>
    <!INCOMPATIBLE_OBJC_NAME_OVERRIDE!>override fun Int.kotlinFunctionC(kotlinParam: Int): Int = this + kotlinParam<!>
    <!INCOMPATIBLE_OBJC_NAME_OVERRIDE!>override fun Int.kotlinFunctionD(kotlinParam: Int): Int = this + kotlinParam<!>
    <!INCOMPATIBLE_OBJC_NAME_OVERRIDE!>override fun Int.kotlinFunctionE(kotlinParam: Int): Int = this + kotlinParam<!>
}
