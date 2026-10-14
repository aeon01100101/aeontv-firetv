package org.jellyfin.androidtv.aeon

/**
 * A parsed .sphere manifest (see SPHERE_FORMAT.md). The manifest is an Albish
 * program; players read only its literal assignments — this parser implements
 * exactly that subset, mirroring the reference parser in sphere_lib.py.
 * Non-literal assignments (e.g. `year = 1900 + 68`) are skipped, never executed.
 */
data class SphereManifest(
    val title: String?,
    val video: String?,
    val creator: String?,
    val year: String?,
    val description: String?,
    val poster: String?,
    val durationSeconds: Long?,
    val language: String?,
    val chapters: List<SphereChapter>,
) {
    val isPlayable: Boolean get() = !title.isNullOrBlank() && !video.isNullOrBlank()
}

data class SphereChapter(
    val atSeconds: Long,
    val name: String,
)

object SphereManifestParser {
    fun parse(text: String): SphereManifest {
        val fields = parseLiteralAssignments(text)
        val chapters = (fields["chapters"] as? List<*>)?.mapNotNull { entry ->
            val dict = entry as? Map<*, *> ?: return@mapNotNull null
            val at = (dict["at"] as? Number)?.toLong() ?: return@mapNotNull null
            val name = dict["name"] as? String ?: return@mapNotNull null
            SphereChapter(atSeconds = at, name = name)
        } ?: emptyList()
        return SphereManifest(
            title = fields["title"] as? String,
            video = fields["video"] as? String,
            creator = fields["creator"] as? String,
            year = (fields["year"] as? Number)?.let { formatNumber(it) } ?: fields["year"] as? String,
            description = fields["description"] as? String,
            poster = fields["poster"] as? String,
            durationSeconds = (fields["duration"] as? Number)?.toLong(),
            language = fields["language"] as? String,
            chapters = chapters,
        )
    }

    private fun formatNumber(n: Number): String {
        val d = n.toDouble()
        return if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
    }

    // region literal-assignment parser (player subset of Albish)

    private enum class Kind { STRING, NUMBER, IDENT, PUNCT }
    private data class Token(val kind: Kind, val text: String)

    private fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == '#' -> {
                    while (i < text.length && text[i] != '\n') i++
                }
                c == '"' -> {
                    val sb = StringBuilder()
                    var j = i + 1
                    while (j < text.length) {
                        val ch = text[j]
                        if (ch == '\\' && j + 1 < text.length) {
                            sb.append(ch)
                            sb.append(text[j + 1])
                            j += 2
                        } else if (ch == '"') {
                            break
                        } else {
                            sb.append(ch)
                            j++
                        }
                    }
                    tokens.add(Token(Kind.STRING, sb.toString()))
                    i = (j + 1).coerceAtMost(text.length)
                }
                c.isDigit() || (c == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    var j = i
                    if (text[j] == '-') j++
                    while (j < text.length && (text[j].isDigit() || text[j] == '.')) j++
                    tokens.add(Token(Kind.NUMBER, text.substring(i, j)))
                    i = j
                }
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    tokens.add(Token(Kind.IDENT, text.substring(i, j)))
                    i = j
                }
                c in "=[]{},:" -> {
                    tokens.add(Token(Kind.PUNCT, c.toString()))
                    i++
                }
                else -> i++ // stray character: skip, players are forgiving
            }
        }
        return tokens
    }

    private class Cursor(val tokens: List<Token>) {
        var pos = 0
        fun peek(): Token? = tokens.getOrNull(pos)
        fun next(): Token? = tokens.getOrNull(pos++)
    }

    private fun parseValue(cursor: Cursor): Any {
        val token = cursor.next() ?: throw IllegalArgumentException("unexpected end")
        return when (token.kind) {
            Kind.STRING -> unescape(token.text)
            Kind.NUMBER -> token.text.toDoubleOrNull()
                ?.let { if (it == it.toLong().toDouble() && !token.text.contains('.')) it.toLong() else it }
                ?: throw IllegalArgumentException("bad number")
            Kind.IDENT -> when (token.text) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("non-literal identifier")
            }
            Kind.PUNCT -> when (token.text) {
                "[" -> {
                    val list = mutableListOf<Any?>()
                    while (true) {
                        val next = cursor.peek() ?: throw IllegalArgumentException("unterminated list")
                        if (next.kind == Kind.PUNCT && next.text == "]") {
                            cursor.next()
                            break
                        }
                        if (next.kind == Kind.PUNCT && next.text == ",") {
                            cursor.next()
                            continue
                        }
                        list.add(parseValue(cursor))
                    }
                    list
                }
                "{" -> {
                    val map = mutableMapOf<String, Any?>()
                    while (true) {
                        val next = cursor.peek() ?: throw IllegalArgumentException("unterminated dict")
                        if (next.kind == Kind.PUNCT && next.text == "}") {
                            cursor.next()
                            break
                        }
                        if (next.kind == Kind.PUNCT && next.text == ",") {
                            cursor.next()
                            continue
                        }
                        val keyToken = cursor.next() ?: throw IllegalArgumentException("dict key")
                        if (keyToken.kind != Kind.STRING) throw IllegalArgumentException("dict keys must be strings")
                        val colon = cursor.next() ?: throw IllegalArgumentException("dict colon")
                        if (colon.kind != Kind.PUNCT || colon.text != ":") throw IllegalArgumentException("expected ':'")
                        map[unescape(keyToken.text)] = parseValue(cursor)
                    }
                    map
                }
                else -> throw IllegalArgumentException("unexpected punct ${token.text}")
            }
        }
    }

    private fun parseLiteralAssignments(text: String): Map<String, Any?> {
        val tokens = tokenize(text)
        val fields = mutableMapOf<String, Any?>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val next = tokens.getOrNull(i + 1)
            if (token.kind == Kind.IDENT && next != null && next.kind == Kind.PUNCT && next.text == "=") {
                val cursor = Cursor(tokens)
                cursor.pos = i + 2
                try {
                    fields[token.text] = parseValue(cursor)
                    i = cursor.pos
                    continue
                } catch (e: Exception) {
                    i += 2
                    continue
                }
            }
            i++
        }
        return fields
    }

    private fun unescape(raw: String): String {
        if (!raw.contains('\\')) return raw
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(raw[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    // endregion
}
