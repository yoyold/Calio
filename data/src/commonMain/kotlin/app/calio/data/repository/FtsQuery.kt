package app.calio.data.repository

/**
 * Turns what the user typed into something the full-text index will accept.
 *
 * FTS5 has a query language of its own: quotes, parentheses, `*`, `^`, `-`, `NEAR` and `OR` all mean
 * something. Passing a search box straight through would let a single apostrophe turn a search into
 * a syntax error, and a stray `OR` into results nobody asked for.
 *
 * Every word is therefore quoted, which makes it a literal, and the last one gets a `*` so results
 * appear while the word is still being typed.
 */
internal object FtsQuery {

    fun from(input: String): String? {
        val terms = input
            .split(*SEPARATORS)
            .map { it.filterNot(Char::isFtsSyntax) }
            .filter { it.isNotBlank() }

        if (terms.isEmpty()) return null

        return terms.mapIndexed { index, term ->
            val quoted = "\"" + term.replace("\"", "\"\"") + "\""
            if (index == terms.lastIndex) "$quoted*" else quoted
        }.joinToString(" ")
    }

    private val SEPARATORS = charArrayOf(' ', '\t', '\n', '\r')
}

/**
 * Characters FTS5 reads as syntax.
 *
 * They are dropped rather than escaped: someone searching for `(` almost certainly means a bracket
 * inside a title, and dropping it turns the search into a slightly wider one instead of an error.
 */
private fun Char.isFtsSyntax(): Boolean = this in "*^:()-+,"
