// TARGET_BACKEND: JVM
// WITH_REFLECT
import kotlin.test.*

@Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")
@kotlin.jvm.JvmInline
value class S(val string: String)

fun test(s: S) {
    class Local

    val localKClass = Local::class
    val localJClass = localKClass.java

    val kName = localKClass.simpleName
    // See https://youtrack.jetbrains.com/issue/KT-29413
    // assertEquals("Local", kName)
    if (kName != "Local" && kName != "test\$Local") throw AssertionError("Fail KClass: $kName")

    assertTrue { localJClass.isLocalClass }

    val jName = localJClass.simpleName
    if (jName != "Local" && jName != "test\$Local") throw AssertionError("Fail java.lang.Class: $jName")
}

fun box(): String {
    test(S(""))

    return "OK"
}
