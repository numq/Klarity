package io.github.numq.klarity.cleaner

import java.lang.ref.Cleaner

internal object NativeCleaner {
    val cleaner: Cleaner = Cleaner.create()
}