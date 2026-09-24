/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.datacapture.extraction

internal data class Token(val type: TokenType, val lexeme: String)

internal enum class TokenType {
  IDENTIFIER,
  CONTEXT_IDENTIFIER,
  DOLLAR_IDENTIFIER,
  NUMBER,
  STRING,
  TRUE,
  FALSE,
  NULL,
  LEFT_PAREN,
  RIGHT_PAREN,
  LEFT_BRACKET,
  RIGHT_BRACKET,
  DOT,
  COMMA,
  PLUS,
  MINUS,
  STAR,
  SLASH,
  PIPE,
  EQUALS,
  NOT_EQUALS,
  LESS_THAN,
  LESS_THAN_OR_EQUALS,
  GREATER_THAN,
  GREATER_THAN_OR_EQUALS,
  AND,
  OR,
  IN,
  EOF,
}

/** Breaks raw expression text into a token stream that the parser can consume deterministically. */
internal class ExpressionTokenizer(private val source: String) {
  private var index: Int = 0

  /** Scans the source string left to right and emits the full token list plus EOF. */
  fun tokenize(): List<Token> {
    val tokens = mutableListOf<Token>()
    while (!isAtEnd()) {
      skipWhitespace()
      if (isAtEnd()) {
        break
      }

      tokens +=
        when (val current = source[index]) {
          '(' -> {
            index += 1
            Token(TokenType.LEFT_PAREN, "(")
          }

          ')' -> {
            index += 1
            Token(TokenType.RIGHT_PAREN, ")")
          }

          '[' -> {
            index += 1
            Token(TokenType.LEFT_BRACKET, "[")
          }

          ']' -> {
            index += 1
            Token(TokenType.RIGHT_BRACKET, "]")
          }

          '.' -> {
            index += 1
            Token(TokenType.DOT, ".")
          }

          ',' -> {
            index += 1
            Token(TokenType.COMMA, ",")
          }

          '+' -> {
            index += 1
            Token(TokenType.PLUS, "+")
          }

          '-' -> {
            index += 1
            Token(TokenType.MINUS, "-")
          }

          '*' -> {
            index += 1
            Token(TokenType.STAR, "*")
          }

          '/' -> {
            index += 1
            Token(TokenType.SLASH, "/")
          }

          '|' -> {
            index += 1
            Token(TokenType.PIPE, "|")
          }

          '=' -> {
            index += 1
            Token(TokenType.EQUALS, "=")
          }

          '!' -> {
            index += 1
            if (match('=')) {
              Token(TokenType.NOT_EQUALS, "!=")
            } else {
              throw IllegalArgumentException("Unexpected token '!'")
            }
          }

          '<' -> {
            index += 1
            if (match('=')) {
              Token(TokenType.LESS_THAN_OR_EQUALS, "<=")
            } else {
              Token(TokenType.LESS_THAN, "<")
            }
          }

          '>' -> {
            index += 1
            if (match('=')) {
              Token(TokenType.GREATER_THAN_OR_EQUALS, ">=")
            } else {
              Token(TokenType.GREATER_THAN, ">")
            }
          }

          '\'' -> readString()

          '%' -> readPrefixedIdentifier(TokenType.CONTEXT_IDENTIFIER)

          '$' -> readPrefixedIdentifier(TokenType.DOLLAR_IDENTIFIER)

          else ->
            when {
              current.isDigit() -> readNumber()
              current.isIdentifierStart() -> readIdentifier()
              else -> throw IllegalArgumentException("Unexpected token '$current'")
            }
        }
    }

    tokens += Token(TokenType.EOF, "")
    return tokens
  }

  /** Reads a single-quoted string literal and returns its unquoted token value. */
  private fun readString(): Token {
    index += 1
    val start = index
    while (!isAtEnd() && source[index] != '\'') {
      index += 1
    }
    if (isAtEnd()) {
      throw IllegalArgumentException("Unterminated string literal")
    }
    val lexeme = source.substring(start, index)
    index += 1
    return Token(TokenType.STRING, lexeme)
  }

  /** Reads `%name` or `$name`-style identifiers used for context and loop-local variables. */
  private fun readPrefixedIdentifier(tokenType: TokenType): Token {
    index += 1
    val start = index
    while (!isAtEnd() && source[index].isIdentifierPart()) {
      index += 1
    }
    if (start == index) {
      throw IllegalArgumentException("Expected identifier after prefix")
    }
    return Token(tokenType, source.substring(start, index))
  }

  /** Reads an integer or decimal literal starting at the current character. */
  private fun readNumber(): Token {
    val start = index
    while (!isAtEnd() && source[index].isDigit()) {
      index += 1
    }
    if (!isAtEnd() && source[index] == '.') {
      index += 1
      while (!isAtEnd() && source[index].isDigit()) {
        index += 1
      }
    }
    return Token(TokenType.NUMBER, source.substring(start, index))
  }

  /** Reads bare identifiers and upgrades reserved words like `and`, `or`, and `null`. */
  private fun readIdentifier(): Token {
    val start = index
    while (!isAtEnd() && source[index].isIdentifierPart()) {
      index += 1
    }
    val lexeme = source.substring(start, index)
    val type =
      when (lexeme) {
        "true" -> TokenType.TRUE
        "false" -> TokenType.FALSE
        "null" -> TokenType.NULL
        "and" -> TokenType.AND
        "or" -> TokenType.OR
        "in" -> TokenType.IN
        else -> TokenType.IDENTIFIER
      }
    return Token(type, lexeme)
  }

  /** Skips insignificant whitespace between tokens. */
  private fun skipWhitespace() {
    while (!isAtEnd() && source[index].isWhitespace()) {
      index += 1
    }
  }

  /** Conditionally consumes the next character when it matches the expected suffix. */
  private fun match(expected: Char): Boolean {
    if (isAtEnd() || source[index] != expected) {
      return false
    }
    index += 1
    return true
  }

  /** Reports whether the tokenizer has reached the end of the source text. */
  private fun isAtEnd(): Boolean = index >= source.length
}

/** Defines which characters may start an unprefixed identifier in the expression language. */
private fun Char.isIdentifierStart(): Boolean = isLetter() || this == '_'

/** Defines which characters may appear after the first character of an identifier. */
private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_'
