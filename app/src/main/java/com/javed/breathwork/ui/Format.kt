package com.javed.breathwork.ui

fun formatClock(ms: Long): String { val s = (ms + 999) / 1000; return "%d:%02d".format(s / 60, s % 60) }
fun ceilSeconds(ms: Long): Int = ((ms + 999) / 1000).toInt()
