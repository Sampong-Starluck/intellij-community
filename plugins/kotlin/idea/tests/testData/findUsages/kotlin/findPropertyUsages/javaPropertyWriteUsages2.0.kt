// PSI_ELEMENT: org.jetbrains.kotlin.psi.JetParameter
// OPTIONS: usages, skipRead
package server

open class A<T>(open var <caret>foo: T)

open class B: A<String>("") {
    override var foo: String
        get() {
            println("get")
            return ""
        }
        set(value: String) {
            println("set:" + value)
        }
}
