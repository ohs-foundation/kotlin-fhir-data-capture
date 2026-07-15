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

import kotlinx.serialization.Serializable

class ValidationException(val errorMessage: String, val errorPath: String) :
  IllegalStateException(
    if (errorPath.isBlank()) {
      errorMessage
    } else {
      "$errorMessage. Path '$errorPath'"
    }
  )

data class FpOptions(
  val modelProfile: ModelProfile = ModelProfile.FHIR_R4,
  val userFunctions: Map<String, UserFunctionDefinition> = emptyMap(),
)

@Serializable
enum class ModelProfile {
  FHIR_R4
}

data class TemplateExecutionRequest(
  val resource: DynamicNode,
  val templates: List<DynamicNode>,
  val context: Map<String, DynamicNode> = emptyMap(),
  val options: FpOptions = FpOptions(),
  val strict: Boolean = false,
)

data class TemplateExecutionResult(val values: List<DynamicNode>)

typealias DynamicNode = Any?

fun interface UserFunction {
  /** Executes a custom function against the current input collection and evaluated arguments. */
  fun invoke(inputs: List<DynamicNode>, args: List<DynamicNode>): List<DynamicNode>
}

data class UserFunctionDefinition(
  val arity: Set<Int>,
  val nullable: Boolean = false,
  val function: UserFunction,
) {
  /** Verifies that the parsed call site is using one of the arities declared by the function. */
  fun supportsArity(argumentCount: Int): Boolean = argumentCount in arity
}

internal object UndefinedNode {
  override fun toString(): String = "undefined"
}

internal typealias DynamicObject = Map<String, DynamicNode>

internal typealias DynamicArray = List<DynamicNode>

internal typealias Path = List<Any>

internal const val ROOT_NODE_KEY: String = "__rootNode__"

/** Formats a traversal path into a readable dotted path for validation and debug messages. */
internal fun formatPath(path: Path): String =
  path.filterNot { it == ROOT_NODE_KEY }.joinToString(".") { it.toString() }

/** Throws a template validation error that includes the exact node path being processed. */
internal fun validationError(message: String, path: Path): Nothing =
  throw ValidationException(message, formatPath(path))

/** Safely views an arbitrary dynamic node as an object map used by the template engine. */
internal fun DynamicNode.asObject(): DynamicObject? = this as? DynamicObject

/** Safely views an arbitrary dynamic node as a dynamic array. */
internal fun DynamicNode.asArray(): DynamicArray? = this as? DynamicArray

/** Normalizes scalars, arrays, nulls, and undefined values into a list-based evaluation shape. */
internal fun normalizeToCollection(value: DynamicNode): List<DynamicNode> =
  when (value) {
    null -> emptyList()
    UndefinedNode -> emptyList()
    is List<*> -> value as List<DynamicNode>
    else -> listOf(value)
  }

/**
 * Recursively removes nested collections so template results can be emitted as a flat list of
 * nodes.
 */
internal fun flattenCollection(values: List<DynamicNode>): List<DynamicNode> {
  val flattened = buildList {
    values.forEach { value ->
      when (value) {
        null -> {}
        UndefinedNode -> {}
        is List<*> -> addAll(flattenCollection(value as List<DynamicNode>))
        else -> add(value)
      }
    }
  }
  return flattened
}

/** Removes a directive key from an object once the directive has been applied. */
internal fun omitKey(value: DynamicObject, key: String?): DynamicObject =
  if (key == null) value else value.filterKeys { it != key }

/** Converts any dynamic value into the string form expected by string-template interpolation. */
internal fun stringifyValue(value: DynamicNode): String =
  when (value) {
    null -> "null"
    is String -> value
    else -> value.toString()
  }
