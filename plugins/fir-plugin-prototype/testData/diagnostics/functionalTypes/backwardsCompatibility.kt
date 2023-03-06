// MODULE: lib
// DISABLE_PLUGINS
// FILE: lib.kt
import org.jetbrains.kotlin.fir.plugin.MyComposable;

fun f(block: @MyComposable () -> Unit) {}

// MODULE: main
// DEPENDENCY: lib Binary
// FILE: test.kt
import org.jetbrains.kotlin.fir.plugin.MyComposable

fun test() {
    val l0: () -> Unit = {}
    f(l0)
    f(@MyComposable {})
    f {}
}
