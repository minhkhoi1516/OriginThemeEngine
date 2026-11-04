package dev.origin.ote.root

import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap

/** Retain only numeric POSIX launch categories, never exception messages or paths. */
internal fun suLaunchFailure(error: IOException): Pair<Int, String> {
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var cause: Throwable? = error
    repeat(6) {
        val current = cause ?: return 127 to "su_start_failed"
        if (!visited.add(current)) return 127 to "su_start_failed"
        val errno = Regex("\\berror=(\\d{1,3})\\b").find(current.message.orEmpty())
            ?.groupValues?.get(1)?.toIntOrNull()
        when (errno) {
            2 -> return 127 to "su_not_found"
            1, 13 -> return 126 to "su_start_denied"
            8 -> return 126 to "su_exec_format_error"
        }
        cause = current.cause
    }
    return 127 to "su_start_failed"
}
