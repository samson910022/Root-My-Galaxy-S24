package dev.samson.s24root

object NativeProbe {
    init {
        System.loadLibrary("s24_native")
    }

    external fun run(): String

    external fun isKernelSuActive(): Boolean
}
