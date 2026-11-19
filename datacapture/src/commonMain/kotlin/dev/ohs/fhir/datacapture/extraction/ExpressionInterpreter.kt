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
 * Evaluates the lightweight expression language used inside template placeholders and directives.
 *
 * It operates on generic JSON-like nodes so the same evaluator can work for QuestionnaireResponse
 * payloads, intermediate loop items, and temporary context variables.
 */
internal class ExpressionInterpreter(private val options: FpOptions, private val strict: Boolean) {
  /** Tokenizes, parses, and evaluates a single expression against the current frame. */
  fun evaluate(
    resource: DynamicNode,
    expression: String,
    context: Map<String, DynamicNode>,
  ): List<DynamicNode> {
    if (expression.trim() == "{}") {
      return emptyList()
    }
    val tokens = ExpressionTokenizer(expression).tokenize()
    val ast = ExpressionParser(tokens).parse()
    return evaluateNode(ast, EvaluationFrame(resource, context))
  }

  /** Dispatches the AST node to the matching evaluator for its concrete node type. */
  private fun evaluateNode(node: ExpressionNode, frame: EvaluationFrame): List<DynamicNode> =
    when (node) {
      is ExpressionNode.Literal -> normalizeToCollection(node.value)
      is ExpressionNode.Variable -> evaluateVariable(node, frame)
      is ExpressionNode.Property -> evaluateProperty(node, frame)
      is ExpressionNode.Index -> evaluateIndex(node, frame)
      is ExpressionNode.Binary -> evaluateBinary(node, frame)
      is ExpressionNode.FunctionCall -> evaluateFunction(node, frame)
    }

  /** Resolves variable access from either explicit context, `$` locals, or the active resource. */
  private fun evaluateVariable(
    variable: ExpressionNode.Variable,
    frame: EvaluationFrame,
  ): List<DynamicNode> =
    when (variable.type) {
      VariableType.CONTEXT -> normalizeToCollection(frame.context[variable.name])

      VariableType.DOLLAR -> normalizeToCollection(frame.context["$${variable.name}"])

      VariableType.RESOURCE -> {
        if (strict) {
          error(
            "Forbidden access to resource property '${variable.name}' in strict mode. Use context instead."
          )
        }
        resolveResourceName(frame.resource, variable.name)
      }
    }

  /** Applies property lookup to every value produced by the target expression. */
  private fun evaluateProperty(
    property: ExpressionNode.Property,
    frame: EvaluationFrame,
  ): List<DynamicNode> {
    val targets = evaluateNode(property.target, frame)
    return buildList {
      targets.forEach { target -> addAll(resolveProperty(target, property.name)) }
    }
  }

  /** Resolves indexed access like `collection[0]` on the first numeric index result. */
  private fun evaluateIndex(
    indexNode: ExpressionNode.Index,
    frame: EvaluationFrame,
  ): List<DynamicNode> {
    val values = evaluateNode(indexNode.target, frame)
    val requestedIndex =
      evaluateNode(indexNode.index, frame).firstOrNull()?.toIndex() ?: return emptyList()
    return values.getOrNull(requestedIndex)?.let(::listOf) ?: emptyList()
  }

  /** Evaluates binary operators such as math, comparison, union, membership, and boolean logic. */
  private fun evaluateBinary(
    binary: ExpressionNode.Binary,
    frame: EvaluationFrame,
  ): List<DynamicNode> =
    when (binary.operator) {
      Operator.UNION -> evaluateNode(binary.left, frame) + evaluateNode(binary.right, frame)

      Operator.AND ->
        listOf(
          asBoolean(evaluateNode(binary.left, frame)) &&
            asBoolean(evaluateNode(binary.right, frame))
        )

      Operator.OR ->
        listOf(
          asBoolean(evaluateNode(binary.left, frame)) ||
            asBoolean(evaluateNode(binary.right, frame))
        )

      Operator.PLUS ->
        evaluateArithmetic(binary, frame) { left, right ->
          if (left is String || right is String) {
            left.toString() + right.toString()
          } else {
            numericResult(left, right) { leftValue, rightValue -> leftValue + rightValue }
          }
        }

      Operator.MINUS ->
        evaluateArithmetic(binary, frame) { left, right ->
          numericResult(left, right) { leftValue, rightValue -> leftValue - rightValue }
        }

      Operator.MULTIPLY ->
        evaluateArithmetic(binary, frame) { left, right ->
          numericResult(left, right) { leftValue, rightValue -> leftValue * rightValue }
        }

      Operator.DIVIDE ->
        evaluateArithmetic(binary, frame) { left, right ->
          numericResult(left, right) { leftValue, rightValue -> leftValue / rightValue }
        }

      Operator.EQUALS -> evaluateComparison(binary, frame) { left, right -> left == right }

      Operator.NOT_EQUALS -> evaluateComparison(binary, frame) { left, right -> left != right }

      Operator.LESS_THAN ->
        evaluateComparison(binary, frame) { left, right -> left.toNumber() < right.toNumber() }

      Operator.LESS_THAN_OR_EQUALS ->
        evaluateComparison(binary, frame) { left, right -> left.toNumber() <= right.toNumber() }

      Operator.GREATER_THAN ->
        evaluateComparison(binary, frame) { left, right -> left.toNumber() > right.toNumber() }

      Operator.GREATER_THAN_OR_EQUALS ->
        evaluateComparison(binary, frame) { left, right -> left.toNumber() >= right.toNumber() }

      Operator.IN -> {
        val left = evaluateNode(binary.left, frame)
        val right = evaluateNode(binary.right, frame)
        if (left.isEmpty() || right.isEmpty()) {
          emptyList()
        } else {
          listOf(left.any { item -> right.any { candidate -> item == candidate } })
        }
      }
    }

  /**
   * Evaluates built-in collection helpers plus user-defined functions.
   *
   * This is where template expressions gain higher-level traversal features such as `where`,
   * `select`, and `repeat`.
   */
  private fun evaluateFunction(
    function: ExpressionNode.FunctionCall,
    frame: EvaluationFrame,
  ): List<DynamicNode> {
    if (function.target == null && function.name == "iif") {
      if (function.arguments.size != 3) {
        error("iif wrong arity: got ${function.arguments.size}")
      }
      val condition = asBoolean(evaluateNode(function.arguments[0], frame))
      return evaluateNode(if (condition) function.arguments[1] else function.arguments[2], frame)
    }

    val targetValues = function.target?.let { evaluateNode(it, frame) } ?: emptyList()
    return when (function.name) {
      "where" -> {
        val predicate =
          function.arguments.singleOrNull()
            ?: error("where wrong arity: got ${function.arguments.size}")
        targetValues.filter { value ->
          asBoolean(
            evaluateNode(
              predicate,
              frame.copy(resource = value, context = frame.context + mapOf("\$this" to value)),
            )
          )
        }
      }

      "select" -> {
        val projection =
          function.arguments.singleOrNull()
            ?: error("select wrong arity: got ${function.arguments.size}")
        buildList {
          targetValues.forEach { value ->
            addAll(
              evaluateNode(
                projection,
                frame.copy(resource = value, context = frame.context + mapOf("\$this" to value)),
              )
            )
          }
        }
      }

      "repeat" -> {
        val propertyName = function.arguments.singleOrNull().toRepeatPropertyName()
        collectRepeat(targetValues, propertyName)
      }

      "exists" -> {
        if (function.arguments.isNotEmpty()) {
          error("exists wrong arity: got ${function.arguments.size}")
        }
        listOf(targetValues.isNotEmpty())
      }

      "first" -> {
        if (function.arguments.isNotEmpty()) {
          error("first wrong arity: got ${function.arguments.size}")
        }
        targetValues.firstOrNull()?.let(::listOf) ?: emptyList()
      }

      "last" -> {
        if (function.arguments.isNotEmpty()) {
          error("last wrong arity: got ${function.arguments.size}")
        }
        targetValues.lastOrNull()?.let(::listOf) ?: emptyList()
      }

      "split" -> {
        val delimiter =
          evaluateNode(
              function.arguments.singleOrNull()
                ?: error("split wrong arity: got ${function.arguments.size}"),
              frame,
            )
            .firstOrNull()
            ?.toString() ?: return emptyList()
        buildList {
          targetValues.forEach { value ->
            val stringValue = value as? String ?: return@forEach
            addAll(stringValue.split(delimiter))
          }
        }
      }

      "exclude" -> {
        val exclusions =
          evaluateNode(
            function.arguments.singleOrNull()
              ?: error("exclude wrong arity: got ${function.arguments.size}"),
            frame,
          )
        targetValues.filterNot { candidate -> exclusions.any { it == candidate } }
      }

      "children" -> {
        buildList {
          targetValues.forEach { value ->
            val objectValue = value.asObject() ?: return@forEach
            objectValue.values.forEach { nested -> addAll(normalizeToCollection(nested)) }
          }
        }
      }

      else -> evaluateUserFunction(function.name, targetValues, function.arguments, frame)
    }
  }

  /** Executes a registered custom function after validating arity and evaluating its arguments. */
  private fun evaluateUserFunction(
    name: String,
    targetValues: List<DynamicNode>,
    arguments: List<ExpressionNode>,
    frame: EvaluationFrame,
  ): List<DynamicNode> {
    val definition: UserFunctionDefinition =
      options.userFunctions[name] ?: error("Unknown function '$name'")
    if (!definition.supportsArity(arguments.size)) {
      error("$name wrong arity: got ${arguments.size}")
    }
    val args = arguments.map { argument -> evaluateNode(argument, frame).firstOrNull() }
    return definition.function.invoke(targetValues, args)
  }

  /**
   * Evaluates binary arithmetic-style operators that consume only the first value from each side.
   */
  private fun evaluateArithmetic(
    binary: ExpressionNode.Binary,
    frame: EvaluationFrame,
    operation: (DynamicNode, DynamicNode) -> DynamicNode,
  ): List<DynamicNode> {
    val left = evaluateNode(binary.left, frame).firstOrNull() ?: return emptyList()
    val right = evaluateNode(binary.right, frame).firstOrNull() ?: return emptyList()
    return listOf(operation(left, right))
  }

  /** Evaluates pairwise comparisons and returns true when any left/right combination matches. */
  private fun evaluateComparison(
    binary: ExpressionNode.Binary,
    frame: EvaluationFrame,
    predicate: (DynamicNode, DynamicNode) -> Boolean,
  ): List<DynamicNode> {
    val left = evaluateNode(binary.left, frame)
    val right = evaluateNode(binary.right, frame)
    if (left.isEmpty() || right.isEmpty()) {
      return emptyList()
    }
    return listOf(
      left.any { leftValue -> right.any { rightValue -> predicate(leftValue, rightValue) } }
    )
  }

  /**
   * Resolves a root-level resource member or the resource itself when the name matches
   * `resourceType`.
   */
  private fun resolveResourceName(resource: DynamicNode, name: String): List<DynamicNode> {
    val objectValue = resource.asObject() ?: return emptyList()
    if (objectValue.containsKey(name)) {
      return normalizeToCollection(objectValue[name])
    }
    val resourceType = objectValue["resourceType"] as? String
    return if (resourceType == name) {
      listOf(resource)
    } else {
      emptyList()
    }
  }

  /**
   * Resolves an object property, including FHIR choice-type convenience access for `value[x]` when
   * the model profile is set to `FHIR_R4`.
   */
  private fun resolveProperty(target: DynamicNode, name: String): List<DynamicNode> {
    val objectValue = target.asObject() ?: return emptyList()
    if (objectValue.containsKey(name)) {
      return normalizeToCollection(objectValue[name])
    }
    if (options.modelProfile == ModelProfile.FHIR_R4 && name == "value") {
      val choiceKeys = objectValue.keys.filter { it.startsWith("value") && it.length > 5 }
      if (choiceKeys.size == 1) {
        return normalizeToCollection(objectValue[choiceKeys.first()])
      }
    }
    return emptyList()
  }

  /** Flattens recursive property traversal for the custom `repeat()` function. */
  private fun collectRepeat(values: List<DynamicNode>, propertyName: String): List<DynamicNode> =
    buildList {
      values.forEach { value -> collectRepeatInto(value, propertyName, this) }
    }

  /** Recursively walks nested lists/objects and accumulates every matching descendant property. */
  private fun collectRepeatInto(
    value: DynamicNode,
    propertyName: String,
    output: MutableList<DynamicNode>,
  ) {
    when (value) {
      null -> Unit

      is List<*> -> value.forEach { nested -> collectRepeatInto(nested, propertyName, output) }

      is Map<*, *> -> {
        val objectValue = value as Map<String, DynamicNode>
        val direct = normalizeToCollection(objectValue[propertyName])
        direct.forEach { nested ->
          output += nested
          collectRepeatInto(nested, propertyName, output)
        }
      }

      else -> Unit
    }
  }

  /** Extracts the property name argument accepted by the `repeat()` helper. */
  private fun ExpressionNode?.toRepeatPropertyName(): String =
    when (this) {
      is ExpressionNode.Variable -> name
      is ExpressionNode.Literal -> value?.toString() ?: error("repeat requires property name")
      else -> error("repeat requires property name")
    }

  /** Applies the engine's truthiness rules to the first value in a collection. */
  private fun asBoolean(values: List<DynamicNode>): Boolean {
    val first = values.firstOrNull() ?: return false
    return when (first) {
      is Boolean -> first
      is Number -> first.toDouble() != 0.0
      is String -> first.isNotBlank()
      else -> true
    }
  }

  /** Converts a dynamic value to a number for arithmetic and comparison operators. */
  private fun DynamicNode.toNumber(): Double =
    when (this) {
      is Number -> toDouble()
      is String -> toDouble()
      else -> error("Expected numeric value, got $this")
    }

  /**
   * Preserves integer-looking arithmetic results as `Long` values instead of always using `Double`.
   */
  private fun numericResult(
    left: DynamicNode,
    right: DynamicNode,
    operation: (Double, Double) -> Double,
  ): DynamicNode {
    val result = operation(left.toNumber(), right.toNumber())
    return if (result % 1.0 == 0.0) {
      result.toLong()
    } else {
      result
    }
  }

  /** Converts supported numeric node types into collection indexes. */
  private fun DynamicNode.toIndex(): Int? =
    when (this) {
      is Int -> this
      is Long -> toInt()
      is Double -> toInt()
      else -> null
    }

  /** Normalizes interpreter failures under a single exception type for the template engine. */
  private fun error(message: String): Nothing = throw IllegalArgumentException(message)
}

private data class EvaluationFrame(val resource: DynamicNode, val context: Map<String, DynamicNode>)
