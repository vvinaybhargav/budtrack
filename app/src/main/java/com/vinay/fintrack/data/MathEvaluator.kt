package com.vinay.fintrack.data

import java.util.Locale

/**
 * Safe arithmetic expression evaluator for amount input fields.
 * Supports +, -, *, /, parenthesis, and decimals.
 * Examples: "56+4" -> 60.0, "100-20.5" -> 79.5, "50*2" -> 100.0, "150/3" -> 50.0
 */
object MathEvaluator {

    fun hasMathOperation(expr: String): Boolean {
        val clean = expr.trim()
        if (clean.startsWith("-") && clean.drop(1).all { it.isDigit() || it == '.' }) return false
        return clean.any { it == '+' || it == '-' || it == '*' || it == '/' || it == 'x' || it == 'X' || it == '÷' }
    }

    fun evaluate(expression: String): Double? {
        val sanitized = expression.replace("₹", "").replace(",", "").trim()
        if (sanitized.isBlank()) return null

        // If it's a simple number already, parse directly
        sanitized.toDoubleOrNull()?.let { return it }

        return runCatching {
            Parser(sanitized).parse()
        }.getOrNull()
    }

    fun formatResult(value: Double): String {
        return if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", value)
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun parse(): Double {
            val result = parseExpression()
            skipWhitespace()
            if (pos < src.length) throw IllegalArgumentException("Unexpected character at $pos: ${src[pos]}")
            return result
        }

        // expression = term { ("+" | "-") term }
        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                skipWhitespace()
                if (consume('+')) {
                    x += parseTerm()
                } else if (consume('-')) {
                    x -= parseTerm()
                } else {
                    return x
                }
            }
        }

        // term = factor { ("*" | "/") factor }
        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                skipWhitespace()
                if (consume('*') || consume('x') || consume('X')) {
                    x *= parseFactor()
                } else if (consume('/') || consume('÷')) {
                    val divisor = parseFactor()
                    if (divisor == 0.0) throw ArithmeticException("Division by zero")
                    x /= divisor
                } else {
                    return x
                }
            }
        }

        // factor = ("+" | "-") factor | "(" expression ")" | number
        private fun parseFactor(): Double {
            skipWhitespace()
            if (consume('+')) return parseFactor()
            if (consume('-')) return -parseFactor()

            if (consume('(')) {
                val x = parseExpression()
                skipWhitespace()
                if (!consume(')')) throw IllegalArgumentException("Missing closing parenthesis")
                return x
            }

            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) {
                pos++
            }
            if (start == pos) throw IllegalArgumentException("Expected number at position $pos")
            return src.substring(start, pos).toDouble()
        }

        private fun consume(char: Char): Boolean {
            skipWhitespace()
            if (pos < src.length && src[pos] == char) {
                pos++
                return true
            }
            return false
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
