import Kt

private func testObjCNameClassNames() throws {
    try assertEquals(actual: ObjCNameC1A().foo(), expected: "a")
    try assertEquals(actual: ObjCNameC1B().foo(), expected: "b")
}

private func testObjCNameFunctionName() throws {
    try assertEquals(actual: ObjCNameAKt.with(userId: "abc"), expected: "abc")
}

private func testObjCNameParameterNames() throws {
    try assertEquals(actual: ObjCNameAKt.supports(true), expected: true)
    try assertEquals(actual: ObjCNameAKt.scanForPeripherals(withServices: 123, options: "abc"), expected: "123 abc")
    try assertEquals(actual: ObjCNameAKt.registerForConnectionEvents(options: "abc"), expected: "abc")
}

private func testObjCNameReceiverName() throws {
    let object = SwiftNameC2()
    try assertEquals(actual: ObjCNameBKt.getSomeValue(of: object), expected: 0)
}

private func testObjCNameMySwiftArray() throws {
    let array = MySwiftArray()
    try assertEquals(actual: array.count, expected: 0)
    try assertEquals(actual: array.index(of: 1), expected: 1)
}

private func testObjCNameOverrides() throws {
    let object = SwiftNameC2()
    try assertEquals(actual: object.someOtherValue, expected: 0)
    object.someOtherValue = 1
    try assertEquals(actual: object.someOtherValue, expected: 1)
    try assertEquals(actual: object.someOtherFunction(receiver: 2, otherParam: 4), expected: 8)
    try assertEquals(actual: ObjCNameC4().foo(objCReceiver: 3, objCParam: 5), expected: 15)
}

private func testObjCNameNestedClass() throws {
    let object = SwiftNameC2.SwiftNestedClass()
    try assertEquals(actual: object.nestedValue, expected: 1)
    object.nestedValue = 2
    try assertEquals(actual: object.nestedValue, expected: 2)
}

private func testObjCNameExact() throws {
    try assertEquals(actual: SwiftExactNestedClass().nestedValue, expected: 1)
    try assertEquals(actual: SwiftNameC3.SwiftNestedClass().nestedValue, expected: 2)
}

private func testObjCNameObject() throws {
    try assertSame(actual: ObjCNameSwiftObject.shared, expected: ObjCNameSwiftObject())
}

class ObjCNameTests : SimpleTestProvider {
    override init() {
        super.init()

        test("TestObjCNameClassNames", testObjCNameClassNames)
        test("TestObjCNameFunctionName", testObjCNameFunctionName)
        test("TestObjCNameParameterNames", testObjCNameParameterNames)
        test("TestObjCNameReceiverName", testObjCNameReceiverName)
        test("TestObjCNameMySwiftArray", testObjCNameMySwiftArray)
        test("TestObjCNameOverrides", testObjCNameOverrides)
        test("TestObjCNameNestedClass", testObjCNameNestedClass)
        test("TestObjCNameExact", testObjCNameExact)
        test("TestObjCNameObject", testObjCNameObject)
    }
}
