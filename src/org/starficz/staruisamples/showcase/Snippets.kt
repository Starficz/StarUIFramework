package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.Global
import java.awt.Color

/**
 * The showcase's code panes: the lines of its own source between `// snippet: <id>` and `// end snippet`, read at
 * runtime from the mod's `src/` folder (`SettingsAPI.loadText`, a path inside the mod), so the code shown is the code
 * running beside it. Coloured for a vanilla label: the pieces to colour, in the order they appear (vanilla's
 * highlights match in order, each from the end of the last, at word boundaries).
 */
internal object Snippets {
    private const val SOURCE_FOLDER = "src/org/starficz/staruisamples/showcase/"
    private val START = Regex("""^\s*//\s*snippet:\s*(\S+)\s*$""")
    private val END = Regex("""^\s*//\s*end snippet\s*$""")

    class Snippet(val text: String, val highlights: List<Pair<String, Color>>)

    sealed class Lookup {
        class Found(val snippet: Snippet) : Lookup()
        /** The file is not there to read (a build without `src/`). */
        object NoSource : Lookup()
        /** The file has no snippet of that id: a typo in a card or a marker. */
        object Missing : Lookup()
    }

    private val files = HashMap<String, Map<String, Snippet>?>()
    private var generation: String? = null

    fun lookup(file: String, id: String): Lookup {
        // a hot reload changes the sources under a running game: read them again then
        val now = System.getProperty("starsector.hotreload.generation")
        if (now != generation) { files.clear(); generation = now }
        val snippets = files.getOrPut(file) { read(file) } ?: return Lookup.NoSource
        return snippets[id]?.let { Lookup.Found(it) } ?: Lookup.Missing
    }

    private fun read(file: String): Map<String, Snippet>? {
        val text = try {
            Global.getSettings().loadText(SOURCE_FOLDER + file)
        } catch (e: Exception) {
            return null
        }
        // an id may mark several ranges (a demo's state, kept outside its builder, and the demo): shown in order,
        // a blank line between them
        val parts = LinkedHashMap<String, MutableList<String>>()
        var id: String? = null
        val lines = ArrayList<String>()
        for (line in text.replace("\r", "").replace("\t", "    ").split('\n')) {
            val start = START.find(line)
            when {
                start != null -> { id = start.groupValues[1]; lines.clear() }
                id != null && END.matches(line) -> { parts.getOrPut(id) { ArrayList() }.add(dedent(lines)); id = null }
                id != null -> lines.add(line)
            }
        }
        return parts.mapValues { (_, texts) -> texts.joinToString("\n\n").let { Snippet(it, colour(it)) } }
    }

    private fun dedent(raw: List<String>): String {
        val lines = raw.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        val indent = lines.filter { it.isNotBlank() }.minOfOrNull { it.length - it.trimStart().length } ?: 0
        return lines.joinToString("\n") { if (it.isBlank()) "" else it.substring(indent).trimEnd() }
    }

    private val KEYWORDS = setOf(
        "val", "var", "fun", "if", "else", "when", "for", "in", "while", "do", "return", "true", "false", "null",
        "object", "class", "is", "as", "by", "this", "private", "internal", "override", "data", "enum", "sealed",
        "import", "package", "break", "continue", "try", "catch", "finally", "throw", "lateinit", "const", "open"
    )

    // IntelliJ's Darcula, with its Kotlin additions (named arguments)
    /** The colour of code that is none of the coloured kinds: names of locals, parameters, types, method calls. */
    val PLAIN = Color(169, 183, 198)
    private val KEYWORD = Color(204, 120, 50)
    private val STRING = Color(106, 135, 89)
    private val NUMBER = Color(104, 151, 187)
    private val COMMENT = Color(128, 128, 128)
    private val PROPERTY = Color(152, 118, 170)
    private val NAMED_ARGUMENT = Color(70, 124, 218)
    private val DECLARATION = Color(255, 198, 109)
    private val ANNOTATION = Color(187, 181, 41)
    /** Component calls (`Button(`, `Column {`): Darcula leaves calls plain; these stand out so the tree reads. */
    private val COMPONENT = Color(255, 198, 109)

    /**
     * The pieces of [text] to colour, in order. Every word is one, plain ones included: vanilla finds each piece from
     * the end of the last, so a plain word left out could take the colour meant for the same word further on.
     *
     * Kinds from the shape of the code, as far as a lexer sees it: a word after a dot is a property unless a call's
     * `(` or `{` follows; a word followed by `=` directly inside a call's parentheses is a named argument; the name
     * after `fun` is a declaration; a capitalised word followed by `(` or `{` is a component call.
     */
    private fun colour(text: String): List<Pair<String, Color>> {
        val out = ArrayList<Pair<String, Color>>()
        val brackets = ArrayList<Char>()
        var declaring = false   // after `fun`, until its parameter list opens
        var i = 0
        fun nextNonSpace(from: Int): Int {
            var k = from
            while (k < text.length && text[k] == ' ') k++
            return k
        }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '/' && text.startsWith("//", i) -> {
                    val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    out += text.substring(i, end) to COMMENT
                    i = end
                }
                c == '"' -> {
                    var j = i + 1
                    while (j < text.length && text[j] != '"' && text[j] != '\n') j += if (text[j] == '\\') 2 else 1
                    val end = minOf(j + 1, text.length)
                    out += text.substring(i, end) to STRING
                    i = end
                }
                c == '@' && i + 1 < text.length && text[i + 1].isLetter() -> {
                    var j = i + 1
                    while (j < text.length && text[j].isLetterOrDigit()) j++
                    out += text.substring(i, j) to ANNOTATION
                    i = j
                }
                c.isDigit() && (i == 0 || !text[i - 1].isLetterOrDigit() && text[i - 1] != '_') -> {
                    var j = i
                    while (j < text.length && (text[j].isDigit() || text[j] == '_' ||
                            text[j] == '.' && j + 1 < text.length && text[j + 1].isDigit())) j++
                    if (j < text.length && text[j] in "fFL") j++
                    out += text.substring(i, j) to NUMBER
                    i = j
                }
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    val word = text.substring(i, j)
                    val afterDot = i > 0 && text[i - 1] == '.'
                    val next = nextNonSpace(j)
                    val opensCall = text.getOrNull(j) == '(' || text.getOrNull(j) == '<' || text.getOrNull(next) == '{'
                    val assigned = text.getOrNull(next) == '=' && text.getOrNull(next + 1) != '='
                    val colour = when {
                        word in KEYWORDS && !afterDot -> KEYWORD
                        declaring && text.getOrNull(j) == '(' -> DECLARATION
                        declaring -> PLAIN   // the receiver type of an extension: fun UIPanelAPI.Name(
                        afterDot && !opensCall -> PROPERTY
                        afterDot -> PLAIN
                        assigned && brackets.lastOrNull() == '(' -> NAMED_ARGUMENT
                        word[0].isUpperCase() && opensCall -> COMPONENT
                        else -> PLAIN
                    }
                    if (word == "fun" && !afterDot) declaring = true
                    out += word to colour
                    i = j
                }
                else -> {
                    when (c) {
                        '(' -> { brackets += c; declaring = false }
                        '[', '{' -> brackets += c
                        ')', ']', '}' -> if (brackets.isNotEmpty()) brackets.removeAt(brackets.size - 1)
                    }
                    i++
                }
            }
        }
        return out
    }
}
