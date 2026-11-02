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
package dev.ohs.fhir.datacapture.expressions

import co.touchlab.kermit.Logger
import dev.ohs.fhir.datacapture.ExternalAnswerValueSetResolver
import dev.ohs.fhir.datacapture.XFhirQueryResolver
import dev.ohs.fhir.datacapture.extensions.answerExpression
import dev.ohs.fhir.datacapture.extensions.answerOptionsToggleExpressions
import dev.ohs.fhir.datacapture.extensions.extractAnswerOptions
import dev.ohs.fhir.datacapture.extensions.isFhirPath
import dev.ohs.fhir.datacapture.extensions.isXFhirQuery
import dev.ohs.fhir.datacapture.fhirpath.ExpressionEvaluator
import dev.ohs.fhir.datacapture.fhirpath.FhirPathService
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ValueSet

/**
 * Evaluates and manages answer options within a [Questionnaire] and its corresponding
 * [QuestionnaireResponse]. It handles enablement, disablement, and presentation of options based on
 * expressions and criteria.
 *
 * The evaluator works in the context of a [Questionnaire] and the corresponding
 * [QuestionnaireResponse]. It is the caller's responsibility to make sure to call the evaluator
 * with [Questionnaire.Item] and [QuestionnaireResponse.Item] that belong to the [Questionnaire] and
 * the [QuestionnaireResponse].
 *
 * @param questionnaire the [Questionnaire] where the expression belong to
 * @param questionnaireResponse the [QuestionnaireResponse] related to the [Questionnaire]
 * @param xFhirQueryResolver the [XFhirQueryResolver] to resolve resources based on the X-FHIR-Query
 * @param externalValueSetResolver the [ExternalAnswerValueSetResolver] to resolve value sets
 *   externally/outside of the [Questionnaire]
 * @param questionnaireItemParentMap the [Map] of items parent
 * @param questionnaireLaunchContextMap the [Map] of launchContext names to their resource values
 */
internal class EnabledAnswerOptionsEvaluator(
  private val questionnaire: Questionnaire,
  private val questionnaireResponse: QuestionnaireResponse,
  private val questionnaireItemParentMap: Map<Questionnaire.Item, Questionnaire.Item> = emptyMap(),
  private val questionnaireLaunchContextMap: Map<String, Resource>? = emptyMap(),
  private val xFhirQueryResolver: XFhirQueryResolver? = null,
  private val externalValueSetResolver: ExternalAnswerValueSetResolver? = null,
) {

  private val expressionEvaluator =
    ExpressionEvaluator(
      questionnaire,
      questionnaireResponse,
      questionnaireItemParentMap,
      questionnaireLaunchContextMap,
      xFhirQueryResolver,
    )

  private val answerValueSetMap = mutableMapOf<String, List<Questionnaire.Item.AnswerOption>>()

  /**
   * The answer expression referencing an x-fhir-query has its evaluated data cached to avoid
   * reloading resources unnecessarily. The value is updated each time an item with answer
   * expression is evaluating the latest answer options.
   */
  private val answerExpressionMap = mutableMapOf<String, List<Questionnaire.Item.AnswerOption>>()

  /**
   * Returns a [Pair] of the enabled/allowed [Questionnaire.Item.AnswerOption] options and the
   * disabled/disallowed [QuestionnaireResponse.Item.Answer] answers, based on the evaluation of
   * [Questionnaire.Item.answerOptionsToggleExpressions] expressions
   */
  internal suspend fun evaluate(
    questionnaireItem: Questionnaire.Item,
    questionnaireResponseItem: QuestionnaireResponse.Item,
  ): Pair<List<Questionnaire.Item.AnswerOption>, List<QuestionnaireResponse.Item.Answer>> {
    val resolvedAnswerOptions = answerOptions(questionnaireItem)

    if (questionnaireItem.answerOptionsToggleExpressions.isEmpty()) {
      return Pair(resolvedAnswerOptions, emptyList())
    }

    val enabledQuestionnaireAnswerOptions =
      evaluateAnswerOptionsToggleExpressions(questionnaireItem, resolvedAnswerOptions)
    val disabledAnswers =
      questionnaireResponseItem.answer
        .takeIf { it.isNotEmpty() }
        ?.filterNot { ans -> enabledQuestionnaireAnswerOptions.any { ans.value == it.value } }
        ?: emptyList()
    return Pair(enabledQuestionnaireAnswerOptions, disabledAnswers)
  }

  /**
   * In a `choice` or `open-choice` type question, the answer options are defined in one of the
   * three elements in the questionnaire:
   * - `Questionnaire.item.answerOption`: a list of permitted answers to the question
   * - `Questionnaire.item.answerValueSet`: a reference to a value set containing a list of
   *   permitted answers to the question
   * - `Extension answer-expression`: an expression based extension which defines the x-fhir-query
   *   or fhirpath to evaluate permitted answer options
   *
   * Returns the answer options defined in one of the sources above. If the answer options are
   * defined in `Questionnaire.item.answerValueSet`, the answer value set will be expanded.
   */
  private suspend fun answerOptions(
    questionnaireItem: Questionnaire.Item
  ): List<Questionnaire.Item.AnswerOption> =
    when {
      questionnaireItem.answerOption.isNotEmpty() -> questionnaireItem.answerOption

      !questionnaireItem.answerValueSet?.value.isNullOrEmpty() ->
        resolveAnswerValueSet(questionnaireItem.answerValueSet!!.value!!)

      questionnaireItem.answerExpression != null -> resolveAnswerExpression(questionnaireItem)

      else -> emptyList()
    }

  private suspend fun resolveAnswerValueSet(uri: String): List<Questionnaire.Item.AnswerOption> {
    // If cache hit, return it
    if (answerValueSetMap.contains(uri)) {
      return answerValueSetMap[uri]!!
    }

    val options: List<Questionnaire.Item.AnswerOption> =
      if (uri.startsWith("#")) {
        // uri is a fragment reference ("#IMMZ.C.DE5"); kotlin-fhir stores contained ids
        // verbatim from JSON (no '#'), so strip the reference's '#' before comparing.
        questionnaire.contained
          .firstOrNull { resource -> resource.id == uri.removePrefix("#") && resource is ValueSet }
          ?.let { resource -> containedValueSetCodings(resource as ValueSet) }
          ?.map { coding ->
            Questionnaire.Item.AnswerOption(
              value = Questionnaire.Item.AnswerOption.Value.Coding(coding)
            )
          }
      } else {
        // Ask the client to provide the answers from an external expanded value set.
        externalValueSetResolver?.resolve(uri)?.map { coding ->
          Questionnaire.Item.AnswerOption(
            value = Questionnaire.Item.AnswerOption.Value.Coding(coding.copy())
          )
        }
      } ?: emptyList()
    // save it so that we avoid have cache misses.
    answerValueSetMap[uri] = options
    return options
  }

  /**
   * Returns the codings of a contained value set: its expansion, or if it has none, the concepts
   * listed in its `compose`.
   */
  private suspend fun containedValueSetCodings(valueSet: ValueSet): List<Coding> {
    val expansion =
      valueSet.expansion
        ?.contains
        .orEmpty()
        .filterNot { it.abstract?.value == true || it.inactive?.value == true }
        .map { component ->
          Coding.Builder()
            .apply {
              system = component.system?.toBuilder()
              code = component.code?.toBuilder()
              display = component.display?.toBuilder()
            }
            .build()
        }
    if (expansion.isNotEmpty()) return expansion

    val compose = valueSet.compose ?: return emptyList()
    val excluded = compose.exclude.flatMap { conceptSetCodings(it) }.map { it.key }.toSet()
    return compose.include
      .flatMap { conceptSetCodings(it) }
      .filterNot { it.key in excluded }
      .distinctBy { it.key }
  }

  private val Coding.key: Pair<String?, String?>
    get() = system?.value to code?.value

  /**
   * Returns the codings of a `compose.include` or `compose.exclude`: the concepts it lists, in
   * every value set it imports. An imported value set is resolved by the
   * [externalValueSetResolver]. A concept set that selects by filter, or takes a whole code system,
   * cannot be listed without the code system and gives no codings.
   */
  private suspend fun conceptSetCodings(conceptSet: ValueSet.Compose.Include): List<Coding> {
    val imported =
      conceptSet.valueSet.mapNotNull { canonical ->
        canonical.value?.let { externalValueSetResolver?.resolve(it).orEmpty() }
      }
    val listed =
      conceptSet.concept.map { concept ->
        Coding.Builder()
          .apply {
            system = conceptSet.system?.toBuilder()
            code = concept.code.toBuilder()
            display = concept.display?.toBuilder()
          }
          .build()
      }
    if (conceptSet.system != null && listed.isEmpty()) {
      Logger.w(
        "Cannot list the codes of ${conceptSet.system?.value} without an expansion: the value set selects by filter or takes the whole code system."
      )
      return emptyList()
    }
    // A code is in the concept set if it is listed (when there is a system) and in every import.
    return (listOfNotNull(listed.takeIf { conceptSet.system != null }) + imported)
      .reduceOrNull { codings, other ->
        val keys = other.map { it.key }.toSet()
        codings.filter { it.key in keys }
      }
      .orEmpty()
  }

  // TODO persist previous answers in case options are changing and new list does not have selected
  // answer and FHIRPath in x-fhir-query
  // https://build.fhir.org/ig/HL7/sdc/expressions.html#x-fhir-query-enhancements
  private suspend fun resolveAnswerExpression(
    item: Questionnaire.Item
  ): List<Questionnaire.Item.AnswerOption> {
    // Check cache first for database queries
    val answerExpression = item.answerExpression ?: return emptyList()

    return when {
      answerExpression.isXFhirQuery -> {
        checkNotNull(xFhirQueryResolver) {
          "XFhirQueryResolver cannot be null. Please provide the XFhirQueryResolver via DataCaptureConfig."
        }
        val variablesMap =
          expressionEvaluator.extractItemDependentVariables(answerExpression, item, null)
        val xFhirExpressionString =
          expressionEvaluator.createXFhirQueryFromExpression(answerExpression, variablesMap)
        if (answerExpressionMap.containsKey(xFhirExpressionString)) {
          return answerExpressionMap[xFhirExpressionString]!!
        }

        val data = xFhirExpressionString?.let { xFhirQueryResolver.resolve(it) } ?: emptyList()
        val options = item.extractAnswerOptions(data)

        xFhirExpressionString?.let { answerExpressionMap[it] = options }
        options
      }

      answerExpression.isFhirPath -> {
        val data = expressionEvaluator.evaluateExpression(item, null, answerExpression)
        item.extractAnswerOptions(data)
      }

      else ->
        throw UnsupportedOperationException(
          "${answerExpression.language} not supported for answer-expression yet"
        )
    }
  }

  private suspend fun evaluateAnswerOptionsToggleExpressions(
    item: Questionnaire.Item,
    answerOptions: List<Questionnaire.Item.AnswerOption>,
  ): List<Questionnaire.Item.AnswerOption> {
    val results =
      item.answerOptionsToggleExpressions
        .map {
          val (expression, toggleOptions) = it
          val evaluationResult =
            if (expression?.value?.isFhirPath == true) {
              FhirPathService.convertToBoolean(
                expressionEvaluator.evaluateExpression(item, null, expression.value)
              )
            } else {
              throw UnsupportedOperationException(
                "${expression?.value?.language} not supported yet for answer-options-toggle-expression"
              )
            }
          evaluationResult to toggleOptions
        }
        .partition { it.first }
    val (allowed, disallowed) = results
    val allowedOptions = allowed.flatMap { it.second }

    val disallowedOptions =
      disallowed.flatMap {
        it.second.filterNot { option -> allowedOptions.any { type -> type == option } }
      }

    return answerOptions.filterNot { answerOption ->
      disallowedOptions.any { answerOption.value == it }
    }
  }
}
