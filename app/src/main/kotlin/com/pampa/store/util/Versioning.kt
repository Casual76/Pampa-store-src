package com.pampa.store.util

private val numericPart = Regex("\\d+")

fun isVersionNewer(candidate: String, current: String): Boolean {
    val left = numericPart.findAll(candidate.substringBefore('-')).map { it.value.toLongOrNull() ?: 0L }.toList()
    val right = numericPart.findAll(current.substringBefore('-')).map { it.value.toLongOrNull() ?: 0L }.toList()
    val width = maxOf(left.size, right.size)
    for (index in 0 until width) {
        val comparison = left.getOrElse(index) { 0L }.compareTo(right.getOrElse(index) { 0L })
        if (comparison != 0) return comparison > 0
    }
    val candidateIsPreview = candidate.contains(Regex("alpha|beta|rc", RegexOption.IGNORE_CASE))
    val currentIsPreview = current.contains(Regex("alpha|beta|rc", RegexOption.IGNORE_CASE))
    return currentIsPreview && !candidateIsPreview
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1_024L * 1_024L -> "${bytes / 1_024L} KB"
    else -> "%.1f MB".format(bytes / (1_024.0 * 1_024.0))
}
