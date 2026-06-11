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

/**
 * Recursive-descent parser for the template expression language.
 *
 * The parse methods are intentionally split by precedence level so reviewers can trace exactly how
 * an expression is grouped before it reaches the interpreter.
 */
internal class ExpressionParser(private val tokens: List<Token>) {
  private var index: Int = 0

  /** Parses one full expression and ensures there are no trailing tokens left behind. */
  fun parse(): ExpressionNode =
    parseOr().also { consume(TokenType.EOF, "Unexpected expression suffix") }

  /** Parses logical `or`, the lowest-precedence binary operator in the grammar. */
  private fun parseOr(): ExpressionNode {
    var expression = parseAnd()
    while (match(TokenType.OR)) {
      expression = ExpressionNode.Binary(expression, Operator.OR, parseAnd())
    }
    return expression
  }

  /** Parses logical `and`, which binds tighter than `or` but looser than comparisons. */
  private fun parseAnd(): ExpressionNode {
    var expression = parseComparison()
    while (match(TokenType.AND)) {
      expression = ExpressionNode.Binary(expression, Operator.AND, parseComparison())
    }
    return expression
  }

  /** Parses equality, ordering, and `in` membership comparisons. */
  private fun parseComparison(): ExpressionNode {
    var expression = parseUnion()
    while (true) {
      val operator =
        when {
          match(TokenType.EQUALS) -> Operator.EQUALS
          match(TokenType.NOT_EQUALS) -> Operator.NOT_EQUALS
          match(TokenType.LESS_THAN) -> Operator.LESS_THAN
          match(TokenType.LESS_THAN_OR_EQUALS) -> Operator.LESS_THAN_OR_EQUALS
          match(TokenType.GREATER_THAN) -> Operator.GREATER_THAN
          match(TokenType.GREATER_THAN_OR_EQUALS) -> Operator.GREATER_THAN_OR_EQUALS
          match(TokenType.IN) -> Operator.IN
          else -> null
        } ?: break

      expression = ExpressionNode.Binary(expression, operator, parseUnion())
    }
    return expression
  }

  /** Parses the union operator `|`, used to concatenate result collections. */
  private fun parseUnion(): ExpressionNode {
    var expression = parseAdditive()
    while (match(TokenType.PIPE)) {
      expression = ExpressionNode.Binary(expression, Operator.UNION, parseAdditive())
    }
    return expression
  }

  /** Parses addition and subtraction after multiplication/division have already been grouped. */
  private fun parseAdditive(): ExpressionNode {
    var expression = parseMultiplicative()
    while (true) {
      val operator =
        when {
          match(TokenType.PLUS) -> Operator.PLUS
          match(TokenType.MINUS) -> Operator.MINUS
          else -> null
        } ?: break
      expression = ExpressionNode.Binary(expression, operator, parseMultiplicative())
    }
    return expression
  }

  /** Parses multiplication and division, which bind tighter than additive operators. */
  private fun parseMultiplicative(): ExpressionNode {
    var expression = parseUnary()
    while (true) {
      val operator =
        when {
          match(TokenType.STAR) -> Operator.MULTIPLY
          match(TokenType.SLASH) -> Operator.DIVIDE
          else -> null
        } ?: break
      expression = ExpressionNode.Binary(expression, operator, parseUnary())
    }
    return expression
  }

  /** Parses unary negation by rewriting it into a binary subtraction from zero. */
  private fun parseUnary(): ExpressionNode {
    if (match(TokenType.MINUS)) {
      return ExpressionNode.Binary(ExpressionNode.Literal(0L), Operator.MINUS, parseUnary())
    }
    return parsePostfix()
  }

  /** Parses chained postfix operations such as property access, function calls, and indexes. */
  private fun parsePostfix(): ExpressionNode {
    var expression = parsePrimary()

    while (true) {
      expression =
        when {
          match(TokenType.LEFT_BRACKET) -> {
            val indexExpression = parseOr()
            consume(TokenType.RIGHT_BRACKET, "Expected ']' after index")
            ExpressionNode.Index(expression, indexExpression)
          }

          match(TokenType.DOT) -> {
            val name = consumeIdentifier("Expected member name after '.'")
            if (match(TokenType.LEFT_PAREN)) {
              ExpressionNode.FunctionCall(expression, name, parseArguments())
            } else {
              ExpressionNode.Property(expression, name)
            }
          }

          match(TokenType.LEFT_PAREN) -> {
            val variable =
              expression as? ExpressionNode.Variable
                ?: throw IllegalArgumentException("Only functions can be invoked")
            ExpressionNode.FunctionCall(null, variable.name, parseArguments())
          }

          else -> return expression
        }
    }
  }

  /** Parses literals, variables, and parenthesized sub-expressions. */
  private fun parsePrimary(): ExpressionNode =
    when {
      match(TokenType.TRUE) -> ExpressionNode.Literal(true)

      match(TokenType.FALSE) -> ExpressionNode.Literal(false)

      match(TokenType.NULL) -> ExpressionNode.Literal(null)

      match(TokenType.NUMBER) -> previous().lexeme.toNumberLiteral()

      match(TokenType.STRING) -> ExpressionNode.Literal(previous().lexeme)

      match(TokenType.CONTEXT_IDENTIFIER) ->
        ExpressionNode.Variable(previous().lexeme, VariableType.CONTEXT)

      match(TokenType.DOLLAR_IDENTIFIER) ->
        ExpressionNode.Variable(previous().lexeme, VariableType.DOLLAR)

      match(TokenType.IDENTIFIER) ->
        ExpressionNode.Variable(previous().lexeme, VariableType.RESOURCE)

      match(TokenType.LEFT_PAREN) -> {
        val expression = parseOr()
        consume(TokenType.RIGHT_PAREN, "Expected ')' after expression")
        expression
      }

      else -> throw IllegalArgumentException("Expected expression")
    }

  /** Parses a comma-separated function argument list until the closing parenthesis. */
  private fun parseArguments(): List<ExpressionNode> {
    val arguments = mutableListOf<ExpressionNode>()
    if (!check(TokenType.RIGHT_PAREN)) {
      do {
        arguments += parseOr()
      } while (match(TokenType.COMMA))
    }
    consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments")
    return arguments
  }

  /** Consumes a plain identifier token and raises a parser-friendly error when it is missing. */
  private fun consumeIdentifier(message: String): String {
    if (match(TokenType.IDENTIFIER)) {
      return previous().lexeme
    }
    throw IllegalArgumentException(message)
  }

  /** Requires the next token to be of the expected type. */
  private fun consume(type: TokenType, message: String) {
    if (!match(type)) {
      throw IllegalArgumentException(message)
    }
  }

  /** Peeks at the current token type without advancing the parser. */
  private fun check(type: TokenType): Boolean = peek().type == type

  /** Advances the parser when the current token matches the requested type. */
  private fun match(type: TokenType): Boolean {
    if (peek().type != type) {
      return false
    }
    index += 1
    return true
  }

  /** Returns the current token under the parser cursor. */
  private fun peek(): Token = tokens[index]

  /** Returns the most recently consumed token. */
  private fun previous(): Token = tokens[index - 1]
}

/** Converts a numeric token into either a whole-number or decimal literal node. */
private fun String.toNumberLiteral(): ExpressionNode =
  if (contains('.')) {
    ExpressionNode.Literal(toDouble())
  } else {
    ExpressionNode.Literal(toLong())
  }
