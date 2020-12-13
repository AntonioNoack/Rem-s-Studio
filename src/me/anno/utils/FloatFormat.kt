package me.anno.utils

import java.util.*

object FloatFormat {
    fun Float.f3() = "%.3f".format(Locale.ENGLISH,this)
    fun Float.f2() = "%.2f".format(Locale.ENGLISH,this)
    fun Float.f1() = "%.1f".format(Locale.ENGLISH,this)

    fun Double.f3() = "%.3f".format(Locale.ENGLISH,this)
    fun Double.f2() = "%.2f".format(Locale.ENGLISH,this)
    fun Double.f1() = "%.1f".format(Locale.ENGLISH,this)
}