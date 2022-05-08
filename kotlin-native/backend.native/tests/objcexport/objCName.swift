import Kt

private func testObjCNameClassNames() throws {
    try assertEquals(actual: ObjCNameC1A().foo(), expected: "a")
    try assertEquals(actual: ObjCNameC1B().foo(), expected: "b")
}

private func testObjCNameFunctionName() throws {
    try assertEquals(actual: ObjCNameA.with(userId: "abc"), expected: "abc")
}

private func testObjCNameParameterNames() throws {
    try assertEquals(actual: ObjCNameA.supports(true), expected: true)
    try assertEquals(actual: ObjCNameA.scanForPeripherals(withServices: 123, options: "abc"), expected: "123 abc")
    try assertEquals(actual: ObjCNameA.registerForConnectionEvents(options: "abc"), expected: "abc")
}

private func testObjCNameMySwiftArray() throws {
    let array = MySwiftArray()
    try assertEquals(actual: array.count, expected: 0)
    try assertEquals(actual: array.index(of: 1), expected: 1)
}

private func testObjCNameOverrides() throws {
    let object = ObjCNameC2()
    try assertEquals(actual: object.someOtherValue, expected: 0)
    object.someOtherValue = 1
    try assertEquals(actual: object.someOtherValue, expected: 1)
}

class ObjCNameTests : SimpleTestProvider {
    override init() {
        super.init()

        test("TestObjCNameClassNames", testObjCNameClassNames)
        test("TestObjCNameFunctionName", testObjCNameFunctionName)
        test("TestObjCNameParameterNames", testObjCNameParameterNames)
        test("TestObjCNameMySwiftArray", testObjCNameMySwiftArray)
        test("TestObjCNameOverrides", testObjCNameOverrides)
    }
}
