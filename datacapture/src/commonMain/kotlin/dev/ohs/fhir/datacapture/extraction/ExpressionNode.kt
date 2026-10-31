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

internal sealed interface ExpressionNode {
  data class Literal(val value: Any?) : ExpressionNode

  data class Variable(val name: String, val type: VariableType) : ExpressionNode

  data class Binary(val left: ExpressionNode, val operator: Operator, val right: ExpressionNode) :
    ExpressionNode

  data class Property(val target: ExpressionNode, val name: String) : ExpressionNode

  data class Index(val target: ExpressionNode, val index: ExpressionNode) : ExpressionNode

  data class FunctionCall(
    val target: ExpressionNode?,
    val name: String,
    val arguments: List<ExpressionNode>,
  ) : ExpressionNode
}

internal enum class VariableType {
  RESOURCE,
  CONTEXT,
  DOLLAR,
}

internal enum class Operator {
  UNION,
  PLUS,
  MINUS,
  MULTIPLY,
  DIVIDE,
  EQUALS,
  NOT_EQUALS,
  LESS_THAN,
  LESS_THAN_OR_EQUALS,
  GREATER_THAN,
  GREATER_THAN_OR_EQUALS,
  IN,
  AND,
  OR,
}
