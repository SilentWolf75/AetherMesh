package com.example.aethermesh.data

fun String.takeUtf8Bytes(maxBytes: Int): String {
    if (maxBytes <= 0 || isEmpty()) return ""
    var index = 0
    var used = 0
    val result = StringBuilder()
    while (index < length) {
        val codePoint = codePointAt(index)
        val value = String(Character.toChars(codePoint))
        val size = value.toByteArray(Charsets.UTF_8).size
        if (used + size > maxBytes) break
        result.append(value)
        used += size
        index += Character.charCount(codePoint)
    }
    return result.toString()
}
