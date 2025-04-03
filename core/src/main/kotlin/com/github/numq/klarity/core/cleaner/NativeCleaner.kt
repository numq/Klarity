package com.github.numq.klarity.core.cleaner

import java.lang.ref.Cleaner

internal object NativeCleaner {
    val cleaner: Cleaner = Cleaner.create()
}