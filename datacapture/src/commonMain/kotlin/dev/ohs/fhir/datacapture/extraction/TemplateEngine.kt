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
 * Walks a JSON-like template tree and resolves embedded expressions/directives into plain data.
 *
 * The overall flow is:
 * 1. Traverse the template node by node.
 * 2. Apply structural directives such as assign/if/for/merge on object nodes.
 * 3. Evaluate inline expressions on string nodes.
 * 4. Remove undefined branches so the final payload contains only resolved resources.
 */
internal class TemplateEngine(private val options: FpOptions, private val strict: Boolean) {
  private val arrayTemplateRegex = Regex("^\\{\\[\\s*([\\s\\S]+?)\\s*\\]\\}$")
  private val singleTemplateRegex = Regex("\\{\\{(\\+?)\\s*([\\s\\S]+?)\\s*(\\+?)\\}\\}")
  private val contextKeyRegex = Regex("^\\{\\{\\s*(.+?)\\s*\\}\\}$")
  private val forKeyRegex =
    Regex("^\\{%\\s*for\\s+(?:(\\w+?)\\s*,\\s*)?(\\w+?)\\s+in\\s+(.+?)\\s*%\\}$")
  private val ifKeyRegex = Regex("^\\{%\\s*if\\s+(.+?)\\s*%\\}$")
  private val elseKeyRegex = Regex("^\\{%\\s*else\\s*%\\}$")
  private val mergeKeyRegex = Regex("^\\{%\\s*merge\\s*%\\}$")
  private val assignKeyRegex = Regex("^\\{%\\s*assign\\s*%\\}$")

  /**
   * Entry point for template resolution.
   *
   * The incoming resource is also exposed as `"context"` so templates can reference the root
   * payload consistently alongside any explicit caller-provided context variables. Each template in
   * the incoming list is resolved independently, which lets callers extract several resources such
   * as Patient, Observation, and Procedure in one pass.
   */
  fun resolve(
    resource: DynamicNode,
    templates: List<DynamicNode>,
    context: Map<String, DynamicNode>,
  ): List<DynamicNode> =
    templates.mapNotNull { template ->
      val resolved =
        resolveTemplateRecursive(
          startPath = emptyList(),
          resource = resource,
          template = template,
          context = mapOf("context" to resource) + context,
        )
      if (resolved === UndefinedNode || resolved == null) {
        null
      } else {
        resolved
      }
    }

  /**
   * Wraps the current template fragment under a synthetic root so recursive processing can always
   * return a single node, even when directives replace the fragment entirely.
   */
  private fun resolveTemplateRecursive(
    startPath: Path,
    resource: DynamicNode,
    template: DynamicNode,
    context: Map<String, DynamicNode>,
  ): DynamicNode {
    val result =
      iterateNode(
        startPath = startPath,
        node = mapOf(ROOT_NODE_KEY to template),
        context = context,
      ) { path, node, currentContext ->
        processNode(path, resource, node, currentContext)
      }

    return result.asObject()?.get(ROOT_NODE_KEY) ?: UndefinedNode
  }

  /**
   * Decides how to process the current node based on its runtime shape.
   *
   * Objects may contain structural directives, strings may contain inline expressions, and
   * everything else is already considered a resolved literal.
   */
  private fun processNode(
    path: Path,
    resource: DynamicNode,
    node: DynamicNode,
    context: Map<String, DynamicNode>,
  ): ProcessResult {
    if (node is Map<*, *>) {
      val typedNode = node as DynamicObject
      val assignResult = processAssignBlock(path, resource, typedNode, context)
      val assignedNode = assignResult.node as? DynamicObject ?: return assignResult
      val matchers =
        listOf(::processContextBlock, ::processMergeBlock, ::processForBlock, ::processIfBlock)

      matchers.forEach { r ->
        val matchResult = r(path, resource, assignedNode, assignResult.context)
        if (matchResult != null) {
          return ProcessResult(matchResult.node, assignResult.context)
        }
      }

      return assignResult
    }

    if (node is String) {
      return ProcessResult(processTemplateString(path, resource, node, context), context)
    }

    return ProcessResult(node, context)
  }

  /**
   * Performs a depth-first walk over lists and maps, reapplying `transform` after each local
   * rewrite so nested directives can keep reshaping the tree.
   */
  private fun iterateNode(
    startPath: Path,
    node: DynamicNode,
    context: Map<String, DynamicNode>,
    transform: (Path, DynamicNode, Map<String, DynamicNode>) -> ProcessResult,
  ): DynamicNode =
    when (node) {
      is List<*> -> {
        val cleaned = buildList {
          node.forEachIndexed { index, value ->
            val path = startPath + index
            val transformed = transform(path, value, context)
            val iterated = iterateNode(path, transformed.node, transformed.context, transform)
            if (iterated !== UndefinedNode) {
              add(iterated)
            }
          }
        }
        val flattened = flattenCollection(cleaned)
        if (flattened.isEmpty()) UndefinedNode else flattened
      }

      is Map<*, *> -> {
        val cleaned =
          buildMap<String, DynamicNode> {
            (node as DynamicObject).forEach { (key, value) ->
              val path = startPath + key
              val transformed = transform(path, value, context)
              val iterated = iterateNode(path, transformed.node, transformed.context, transform)
              if (iterated !== UndefinedNode) {
                put(key, iterated)
              }
            }
          }
        if (cleaned.isEmpty()) UndefinedNode else cleaned
      }

      else -> transform(startPath, node, context).node
    }

  /**
   * Resolves string-based template syntax.
   *
   * Supported forms are:
   * - `{[ expression ]}` for array-style results
   * - `{{ expression }}` for single-value replacement
   * - embedded `{{ ... }}` segments inside larger literal strings
   */
  private fun processTemplateString(
    path: Path,
    resource: DynamicNode,
    node: String,
    context: Map<String, DynamicNode>,
  ): DynamicNode {
    val arrayMatch = arrayTemplateRegex.matchEntire(node)
    if (arrayMatch != null) {
      return evaluateExpression(path, resource, arrayMatch.groupValues[1], context)
    }

    val matches = singleTemplateRegex.findAll(node).toList()
    if (matches.isEmpty()) {
      return node
    }

    if (matches.size == 1 && matches.first().value == node) {
      val match = matches.first()
      val replacement = evaluateExpression(path, resource, match.groupValues[2], context)
      return replacement.firstOrNull() ?: if (match.groupValues[1] == "+") null else UndefinedNode
    }

    var result = node
    matches.forEach { match ->
      val replacement =
        evaluateExpression(path, resource, match.groupValues[2], context).firstOrNull()
      if (replacement == null) {
        return if (match.groupValues[1] == "+") null else UndefinedNode
      }
      result = result.replace(match.value, stringifyValue(replacement))
    }

    return result
  }

  /**
   * Handles object nodes whose only key is `{{ expression }}`.
   *
   * Each answer produced by the expression becomes the resource/context for resolving the nested
   * object, which effectively maps a template fragment across a collection.
   */
  private fun processContextBlock(
    path: Path,
    resource: DynamicNode,
    node: DynamicObject,
    context: Map<String, DynamicNode>,
  ): MatchResult? {
    val contextKey = node.keys.firstOrNull { key -> contextKeyRegex.matches(key) } ?: return null
    if (node.size > 1) {
      validationError("Context block must be presented as single key", path)
    }
    val expression = contextKeyRegex.matchEntire(contextKey)?.groupValues?.get(1).orEmpty()
    val answers = evaluateExpression(path, resource, expression, context)
    return MatchResult(
      answers.map { answer ->
        resolveTemplateRecursive(path, answer, node.getValue(contextKey), context)
      }
    )
  }

  /**
   * Expands `{% for ... in ... %}` blocks by evaluating the source collection and resolving the
   * body once for each item, injecting loop variables into the context for the body.
   */
  private fun processForBlock(
    path: Path,
    resource: DynamicNode,
    node: DynamicObject,
    context: Map<String, DynamicNode>,
  ): MatchResult? {
    val forKey = node.keys.firstOrNull { key -> forKeyRegex.matches(key) } ?: return null
    if (node.size > 1) {
      validationError("For block must be presented as single key", path)
    }
    val match =
      forKeyRegex.matchEntire(forKey)
        ?: validationError("For block must be presented as single key", path)
    val indexKey = match.groupValues[1].takeIf { it.isNotBlank() }
    val itemKey = if (indexKey != null) match.groupValues[2] else match.groupValues[2]
    val expression = match.groupValues[3]
    val answers = evaluateExpression(path, resource, expression, context)
    return MatchResult(
      answers.mapIndexed { index, answer ->
        resolveTemplateRecursive(
          path,
          resource,
          node.getValue(forKey),
          context +
            buildMap {
              put(itemKey, answer)
              if (indexKey != null) {
                put(indexKey, index.toLong())
              }
            },
        )
      }
    )
  }

  /**
   * Resolves `{% if %}` / `{% else %}` blocks.
   *
   * When the surrounding object has extra keys, the chosen branch is implicitly merged back into
   * the remaining object fields so reviewers can understand the "inline object patch" behavior.
   */
  private fun processIfBlock(
    path: Path,
    resource: DynamicNode,
    node: DynamicObject,
    context: Map<String, DynamicNode>,
  ): MatchResult? {
    val ifKeys = node.keys.filter { key -> ifKeyRegex.matches(key) }
    if (ifKeys.size > 1) {
      validationError("If block must be presented once", path)
    }
    val elseKeys = node.keys.filter { key -> elseKeyRegex.matches(key) }
    if (elseKeys.size > 1) {
      validationError("Else block must be presented once", path)
    }
    val ifKey = ifKeys.firstOrNull()
    val elseKey = elseKeys.firstOrNull()

    if (elseKey != null && ifKey == null) {
      validationError("Else block must be presented only when if block is presented", path)
    }
    if (ifKey == null) {
      return null
    }

    val expression = ifKeyRegex.matchEntire(ifKey)?.groupValues?.get(1).orEmpty()
    val condition =
      evaluateExpression(path, resource, "iif($expression, true, false)", context).firstOrNull()
        as? Boolean ?: false

    val newNode =
      if (condition) {
        resolveTemplateRecursive(path, resource, node.getValue(ifKey), context)
      } else {
        elseKey?.let { resolveTemplateRecursive(path, resource, node.getValue(it), context) }
          ?: UndefinedNode
      }

    val isMergeBehavior = node.size != if (elseKey == null) 1 else 2
    if (isMergeBehavior) {
      if (newNode !is Map<*, *> && newNode != null && newNode !== UndefinedNode) {
        validationError(
          "If/else block must return object for implicit merge into existing node",
          path,
        )
      }
      val merged =
        omitKey(omitKey(node, ifKey), elseKey) + (newNode as? DynamicObject ?: emptyMap())
      return MatchResult(merged)
    }

    return MatchResult(newNode)
  }

  /**
   * Implements `{% merge %}` by resolving one or more child objects and folding their fields into
   * the current object after the directive key itself has been removed.
   */
  private fun processMergeBlock(
    path: Path,
    resource: DynamicNode,
    node: DynamicObject,
    context: Map<String, DynamicNode>,
  ): MatchResult? {
    val mergeKey = node.keys.firstOrNull { key -> mergeKeyRegex.matches(key) } ?: return null
    val merged =
      node[mergeKey]
        .let { if (it is List<*>) it else listOf(it) }
        .fold(omitKey(node, mergeKey)) { acc, value ->
          val result = resolveTemplateRecursive(path, resource, value, context)
          if (result !is Map<*, *> && result != null && result !== UndefinedNode) {
            validationError("Merge block must contain object", path)
          }
          acc + (result as? DynamicObject ?: emptyMap())
        }
    return MatchResult(merged)
  }

  /**
   * Implements `{% assign %}` by resolving temporary values first and storing them in context for
   * the rest of the current object. The directive is then removed from the output object.
   */
  private fun processAssignBlock(
    path: Path,
    resource: DynamicNode,
    node: DynamicObject,
    context: Map<String, DynamicNode>,
  ): ProcessResult {
    val assignKey =
      node.keys.firstOrNull { key -> assignKeyRegex.matches(key) }
        ?: return ProcessResult(node, context)
    val extendedContext = context.toMutableMap()
    val assignedValue = node.getValue(assignKey)

    when (assignedValue) {
      is List<*> -> {
        assignedValue.forEach { item ->
          val entry =
            item as? DynamicObject
              ?: validationError("Assign block must accept array or object", path)
          if (entry.size != 1) {
            validationError("Assign block must accept only one key per object", path)
          }
          val key = entry.keys.first()
          val resolved =
            resolveTemplateRecursive(path + key, resource, entry.getValue(key), extendedContext)
          extendedContext[key] = if (resolved === UndefinedNode) null else resolved
        }
      }

      is Map<*, *> -> {
        val entry = assignedValue as DynamicObject
        if (entry.size != 1) {
          validationError("Assign block must accept only one key per object", path)
        }
        val key = entry.keys.first()
        val resolved =
          resolveTemplateRecursive(path + key, resource, entry.getValue(key), extendedContext)
        extendedContext[key] = if (resolved === UndefinedNode) null else resolved
      }

      else -> validationError("Assign block must accept array or object", path)
    }

    return ProcessResult(omitKey(node, assignKey), extendedContext)
  }

  /**
   * Executes one expression and upgrades any parsing/runtime failure into a template-aware
   * validation exception that includes the exact node path being resolved.
   */
  private fun evaluateExpression(
    path: Path,
    resource: DynamicNode,
    expression: String,
    context: Map<String, DynamicNode>,
  ): List<DynamicNode> =
    try {
      ExpressionInterpreter(options, strict).evaluate(resource, expression, context)
    } catch (exception: ValidationException) {
      throw exception
    } catch (exception: Exception) {
      throw ValidationException(
        errorMessage = "Cannot evaluate '$expression': ${exception.message}",
        errorPath = formatPath(path),
      )
    }
}

private data class ProcessResult(val node: DynamicNode, val context: Map<String, DynamicNode>)

private data class MatchResult(val node: DynamicNode)
