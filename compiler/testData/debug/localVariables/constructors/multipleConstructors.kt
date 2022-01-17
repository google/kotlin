

// FILE: test.kt
open class Base(i: Int)

class Derived(): Base(1) {
    constructor(p: Int): this() {
        val a = 1
    }

    constructor(p1: Int, p2: Int): this()
}

fun box() {
    Derived(1)
    Derived(1, 1)
}

// EXPECTATIONS
// test.kt:15 box:
// test.kt:7 <init>: p:int=1:int
// test.kt:6 <init>:
// test.kt:4 <init>: i:int=1:int
// test.kt:6 <init>:
// test.kt:8 <init>: p:int=1:int
// EXPECTATIONS JVM_IR
// test.kt:9 <init>: p:int=1:int, a:int=1:int
// EXPECTATIONS
// test.kt:15 box:

// test.kt:16 box:
// test.kt:11 <init>: p1:int=1:int, p2:int=1:int
// test.kt:6 <init>:
// test.kt:4 <init>: i:int=1:int
// test.kt:6 <init>:
// test.kt:11 <init>: p1:int=1:int, p2:int=1:int
// test.kt:16 box:
// test.kt:17 box: