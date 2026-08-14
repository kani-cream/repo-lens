package com.kanicream.repolens.format

/** Renders metric values compactly: whole numbers without a decimal part. */
object MetricFormat {
    fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
