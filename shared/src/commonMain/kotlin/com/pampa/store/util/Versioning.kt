package com.pampa.store.util

fun isVersionNewer(candidate: String, current: String): Boolean {
    return compareVersions(candidate, current) > 0
}

fun compareVersions(left: String, right: String): Int {
    val normalizedLeft = left.trim()
    val normalizedRight = right.trim()
    if (normalizedLeft == normalizedRight) return 0

    val semanticLeft = parseSemanticVersion(normalizedLeft)
    val semanticRight = parseSemanticVersion(normalizedRight)
    if (semanticLeft != null && semanticRight != null) {
        return compareSemanticVersions(semanticLeft, semanticRight)
    }

    return compareNaturally(normalizedLeft, normalizedRight)
}

private data class SemanticVersion(
    val core: List<Int>,
    val preRelease: List<VersionIdentifier>
)

private sealed interface VersionIdentifier {
    data class Numeric(val digits: String) : VersionIdentifier
    data class Text(val value: String) : VersionIdentifier
}

private fun parseSemanticVersion(raw: String): SemanticVersion? {
    val withoutPrefix = raw.removePrefix("v").removePrefix("V")
    if (withoutPrefix.isBlank()) return null

    val withoutBuildMetadata = withoutPrefix.substringBefore('+')
    val split = withoutBuildMetadata.split('-', limit = 2)
    val corePart = split.firstOrNull().orEmpty()
    if (corePart.isBlank()) return null

    val core = corePart
        .split('.')
        .map { token ->
            if (token.isBlank() || token.any { !it.isDigit() }) return null
            token.toIntOrNull() ?: return null
        }

    val preRelease = split.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?.split('.')
        ?.map { token ->
            when {
                token.isBlank() -> return null
                token.all { it.isDigit() } -> VersionIdentifier.Numeric(normalizeDigits(token))
                token.all { it.isLetterOrDigit() || it == '-' } -> VersionIdentifier.Text(token.lowercase())
                else -> return null
            }
        }
        ?: emptyList()

    return SemanticVersion(core = core, preRelease = preRelease)
}

private fun compareSemanticVersions(left: SemanticVersion, right: SemanticVersion): Int {
    val coreSize = maxOf(left.core.size, right.core.size)
    for (index in 0 until coreSize) {
        val leftPart = left.core.getOrElse(index) { 0 }
        val rightPart = right.core.getOrElse(index) { 0 }
        if (leftPart != rightPart) return leftPart.compareTo(rightPart)
    }

    if (left.preRelease.isEmpty() && right.preRelease.isEmpty()) return 0
    if (left.preRelease.isEmpty()) return 1
    if (right.preRelease.isEmpty()) return -1

    val preReleaseSize = maxOf(left.preRelease.size, right.preRelease.size)
    for (index in 0 until preReleaseSize) {
        val leftIdentifier = left.preRelease.getOrNull(index) ?: return -1
        val rightIdentifier = right.preRelease.getOrNull(index) ?: return 1
        val comparison = compareIdentifier(leftIdentifier, rightIdentifier)
        if (comparison != 0) return comparison
    }

    return 0
}

private fun compareIdentifier(left: VersionIdentifier, right: VersionIdentifier): Int {
    return when {
        left is VersionIdentifier.Numeric && right is VersionIdentifier.Numeric ->
            compareNormalizedDigits(left.digits, right.digits)

        left is VersionIdentifier.Numeric && right is VersionIdentifier.Text -> -1
        left is VersionIdentifier.Text && right is VersionIdentifier.Numeric -> 1

        left is VersionIdentifier.Text && right is VersionIdentifier.Text ->
            left.value.compareTo(right.value)

        else -> 0
    }
}

private fun compareNaturally(left: String, right: String): Int {
    val leftTokens = tokenize(left)
    val rightTokens = tokenize(right)
    val maxSize = maxOf(leftTokens.size, rightTokens.size)
    for (index in 0 until maxSize) {
        val leftToken = leftTokens.getOrNull(index) ?: return -1
        val rightToken = rightTokens.getOrNull(index) ?: return 1
        if (leftToken == rightToken) continue

        val numericComparison = compareIfNumericTokens(leftToken, rightToken)
        if (numericComparison != null && numericComparison != 0) return numericComparison
        if (numericComparison != null) continue

        val caseInsensitive = leftToken.compareTo(rightToken, ignoreCase = true)
        if (caseInsensitive != 0) return caseInsensitive

        val caseSensitive = leftToken.compareTo(rightToken)
        if (caseSensitive != 0) return caseSensitive
    }

    return left.compareTo(right, ignoreCase = true)
}

private fun compareIfNumericTokens(leftToken: String, rightToken: String): Int? {
    val leftIsNumeric = leftToken.all { it.isDigit() }
    val rightIsNumeric = rightToken.all { it.isDigit() }
    if (!leftIsNumeric || !rightIsNumeric) return null
    return compareNormalizedDigits(normalizeDigits(leftToken), normalizeDigits(rightToken))
}

private fun tokenize(value: String): List<String> {
    if (value.isBlank()) return emptyList()

    val tokens = mutableListOf<String>()
    var start = 0
    var previousWasDigit = value[0].isDigit()
    for (index in 1 until value.length) {
        val currentIsDigit = value[index].isDigit()
        if (currentIsDigit != previousWasDigit) {
            tokens += value.substring(start, index)
            start = index
            previousWasDigit = currentIsDigit
        }
    }
    tokens += value.substring(start)
    return tokens
}

private fun normalizeDigits(token: String): String {
    return token.trimStart('0').ifBlank { "0" }
}

private fun compareNormalizedDigits(left: String, right: String): Int {
    if (left.length != right.length) return left.length.compareTo(right.length)
    return left.compareTo(right)
}
