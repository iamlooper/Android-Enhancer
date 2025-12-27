package io.github.iamlooper.androidenhancer.system.jni

enum class AndroidEnhancerMode(val code: Int) {
    AUTO(0),
    POWERSAVER(1),
    BALANCED(2),
    PERFORMANCE(3),
    GAMING(4);

    companion object {
        fun fromCode(code: Int): AndroidEnhancerMode = when (code) {
            POWERSAVER.code -> POWERSAVER
            BALANCED.code -> BALANCED
            PERFORMANCE.code -> PERFORMANCE
            GAMING.code -> GAMING
            else -> AUTO
        }
    }
}


