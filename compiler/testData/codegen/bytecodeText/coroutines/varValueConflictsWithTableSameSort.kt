// WITH_COROUTINES

import helpers.*
// TREAT_AS_ONE_FILE
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
suspend fun suspendHere(): String = ""

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    var result = "fail 1"

    builder {
        var shiftSlot: String = ""
        // Initialize var with String value
        try {
            var i: String = "abc"
            i = "123"
            // We need to use the variable, otherwise, it is considered dead.
            println(i)
        } finally { }

        // This variable should take the same slot as 'i' had
        var s: String = "FAIL"

        // We shout not spill 's' to continuation field because it's not effectively initialized
        if (suspendHere() == "OK") {
            println(s)
            s = "OK"
        }
        else {
            s = "fail 2"
        }

        result = s
    }

    return result
}

// 1 LOCALVARIABLE i Ljava/lang/String; L.* 3
// 1 PUTFIELD VarValueConflictsWithTableSameSortKt\$box\$1.L\$0 : Ljava/lang/Object;
/* 1 load in result = s */
// 1 ALOAD 3\s+PUTFIELD kotlin/jvm/internal/Ref\$ObjectRef\.element
/* 1 load in spill */
// 1 ALOAD 3\s+PUTFIELD VarValueConflictsWithTableSameSortKt\$box\$1\.L\$0 : Ljava/lang/Object;
/* 2 loads in println(s) */
// 2 ALOAD 3\s+INVOKEVIRTUAL java/io/PrintStream.println \(Ljava/lang/Object;\)V
/* But no further load when spilling 's' to the continuation */
// 4 ALOAD 3

// We merge LVT records for two consequent branches, but we split the local over the restore code.
// JVM_IR_TEMPLATES
// 3 LOCALVARIABLE s Ljava/lang/String; L.* 3
// JVM_TEMPLATES
// 4 LOCALVARIABLE s Ljava/lang/String; L.* 3
