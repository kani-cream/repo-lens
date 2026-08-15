package samples.kotlin

/**
 * Kotlin counterparts, proving the checks work through the same UAST path as Java.
 * Expected findings: one Too Many Parameters and one Deep Nesting, both with symbols.
 */
class KotlinSamples {

    // TODO Kotlin marker: this line is picked up by RL-T001.
    // FIXME Kotlin marker: and so is this one.

    /** Triggers RL-M002: 9 parameters. */
    fun configure(
        host: String,
        port: Int,
        user: String,
        password: String,
        useTls: Boolean,
        timeoutMillis: Int,
        retryCount: Int,
        proxyHost: String,
        proxyPort: Int,
    ) {
        // intentionally empty
    }

    /** Triggers RL-M003: when(1) > forEach lambda(2) > if(3) > for(4) > if(5) > try(6). */
    fun deep(groups: List<List<Int>>) {
        when (groups.size) {                          // 1
            0 -> Unit
            else -> groups.forEach { group ->         // 2
                if (group.isNotEmpty()) {             // 3
                    for (value in group) {            // 4
                        if (value > 0) {              // 5
                            try {                     // 6
                                println(value)
                            } catch (e: RuntimeException) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }
    }
}
