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
package dev.ohs.fhir.datacapture.extraction.definition

import dev.ohs.fhir.model.r4.Account
import dev.ohs.fhir.model.r4.ActivityDefinition
import dev.ohs.fhir.model.r4.AdverseEvent
import dev.ohs.fhir.model.r4.AllergyIntolerance
import dev.ohs.fhir.model.r4.Appointment
import dev.ohs.fhir.model.r4.AppointmentResponse
import dev.ohs.fhir.model.r4.AuditEvent
import dev.ohs.fhir.model.r4.Basic
import dev.ohs.fhir.model.r4.Binary
import dev.ohs.fhir.model.r4.BiologicallyDerivedProduct
import dev.ohs.fhir.model.r4.BodyStructure
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.CapabilityStatement
import dev.ohs.fhir.model.r4.CarePlan
import dev.ohs.fhir.model.r4.CareTeam
import dev.ohs.fhir.model.r4.CatalogEntry
import dev.ohs.fhir.model.r4.ChargeItem
import dev.ohs.fhir.model.r4.ChargeItemDefinition
import dev.ohs.fhir.model.r4.Claim
import dev.ohs.fhir.model.r4.ClaimResponse
import dev.ohs.fhir.model.r4.ClinicalImpression
import dev.ohs.fhir.model.r4.CodeSystem
import dev.ohs.fhir.model.r4.Communication
import dev.ohs.fhir.model.r4.CommunicationRequest
import dev.ohs.fhir.model.r4.CompartmentDefinition
import dev.ohs.fhir.model.r4.Composition
import dev.ohs.fhir.model.r4.ConceptMap
import dev.ohs.fhir.model.r4.Condition
import dev.ohs.fhir.model.r4.Consent
import dev.ohs.fhir.model.r4.Contract
import dev.ohs.fhir.model.r4.Coverage
import dev.ohs.fhir.model.r4.CoverageEligibilityRequest
import dev.ohs.fhir.model.r4.CoverageEligibilityResponse
import dev.ohs.fhir.model.r4.DetectedIssue
import dev.ohs.fhir.model.r4.Device
import dev.ohs.fhir.model.r4.DeviceDefinition
import dev.ohs.fhir.model.r4.DeviceMetric
import dev.ohs.fhir.model.r4.DeviceRequest
import dev.ohs.fhir.model.r4.DeviceUseStatement
import dev.ohs.fhir.model.r4.DiagnosticReport
import dev.ohs.fhir.model.r4.DocumentManifest
import dev.ohs.fhir.model.r4.DocumentReference
import dev.ohs.fhir.model.r4.EffectEvidenceSynthesis
import dev.ohs.fhir.model.r4.Encounter
import dev.ohs.fhir.model.r4.Endpoint
import dev.ohs.fhir.model.r4.EnrollmentRequest
import dev.ohs.fhir.model.r4.EnrollmentResponse
import dev.ohs.fhir.model.r4.EpisodeOfCare
import dev.ohs.fhir.model.r4.EventDefinition
import dev.ohs.fhir.model.r4.Evidence
import dev.ohs.fhir.model.r4.EvidenceVariable
import dev.ohs.fhir.model.r4.ExampleScenario
import dev.ohs.fhir.model.r4.ExplanationOfBenefit
import dev.ohs.fhir.model.r4.FamilyMemberHistory
import dev.ohs.fhir.model.r4.Flag
import dev.ohs.fhir.model.r4.Goal
import dev.ohs.fhir.model.r4.GraphDefinition
import dev.ohs.fhir.model.r4.Group
import dev.ohs.fhir.model.r4.GuidanceResponse
import dev.ohs.fhir.model.r4.HealthcareService
import dev.ohs.fhir.model.r4.ImagingStudy
import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.ImmunizationEvaluation
import dev.ohs.fhir.model.r4.ImmunizationRecommendation
import dev.ohs.fhir.model.r4.ImplementationGuide
import dev.ohs.fhir.model.r4.InsurancePlan
import dev.ohs.fhir.model.r4.Invoice
import dev.ohs.fhir.model.r4.Library
import dev.ohs.fhir.model.r4.Linkage
import dev.ohs.fhir.model.r4.List
import dev.ohs.fhir.model.r4.Location
import dev.ohs.fhir.model.r4.Measure
import dev.ohs.fhir.model.r4.MeasureReport
import dev.ohs.fhir.model.r4.Media
import dev.ohs.fhir.model.r4.Medication
import dev.ohs.fhir.model.r4.MedicationAdministration
import dev.ohs.fhir.model.r4.MedicationDispense
import dev.ohs.fhir.model.r4.MedicationKnowledge
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.MedicationStatement
import dev.ohs.fhir.model.r4.MedicinalProduct
import dev.ohs.fhir.model.r4.MedicinalProductAuthorization
import dev.ohs.fhir.model.r4.MedicinalProductContraindication
import dev.ohs.fhir.model.r4.MedicinalProductIndication
import dev.ohs.fhir.model.r4.MedicinalProductIngredient
import dev.ohs.fhir.model.r4.MedicinalProductInteraction
import dev.ohs.fhir.model.r4.MedicinalProductManufactured
import dev.ohs.fhir.model.r4.MedicinalProductPackaged
import dev.ohs.fhir.model.r4.MedicinalProductPharmaceutical
import dev.ohs.fhir.model.r4.MedicinalProductUndesirableEffect
import dev.ohs.fhir.model.r4.MessageDefinition
import dev.ohs.fhir.model.r4.MessageHeader
import dev.ohs.fhir.model.r4.MolecularSequence
import dev.ohs.fhir.model.r4.NamingSystem
import dev.ohs.fhir.model.r4.NutritionOrder
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.ObservationDefinition
import dev.ohs.fhir.model.r4.OperationDefinition
import dev.ohs.fhir.model.r4.OperationOutcome
import dev.ohs.fhir.model.r4.Organization
import dev.ohs.fhir.model.r4.OrganizationAffiliation
import dev.ohs.fhir.model.r4.Parameters
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.PaymentNotice
import dev.ohs.fhir.model.r4.PaymentReconciliation
import dev.ohs.fhir.model.r4.Person
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Practitioner
import dev.ohs.fhir.model.r4.PractitionerRole
import dev.ohs.fhir.model.r4.Procedure
import dev.ohs.fhir.model.r4.Provenance
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.RelatedPerson
import dev.ohs.fhir.model.r4.RequestGroup
import dev.ohs.fhir.model.r4.ResearchDefinition
import dev.ohs.fhir.model.r4.ResearchElementDefinition
import dev.ohs.fhir.model.r4.ResearchStudy
import dev.ohs.fhir.model.r4.ResearchSubject
import dev.ohs.fhir.model.r4.RiskAssessment
import dev.ohs.fhir.model.r4.RiskEvidenceSynthesis
import dev.ohs.fhir.model.r4.Schedule
import dev.ohs.fhir.model.r4.SearchParameter
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.Slot
import dev.ohs.fhir.model.r4.Specimen
import dev.ohs.fhir.model.r4.SpecimenDefinition
import dev.ohs.fhir.model.r4.StructureDefinition
import dev.ohs.fhir.model.r4.StructureMap
import dev.ohs.fhir.model.r4.Subscription
import dev.ohs.fhir.model.r4.Substance
import dev.ohs.fhir.model.r4.SubstanceNucleicAcid
import dev.ohs.fhir.model.r4.SubstancePolymer
import dev.ohs.fhir.model.r4.SubstanceProtein
import dev.ohs.fhir.model.r4.SubstanceReferenceInformation
import dev.ohs.fhir.model.r4.SubstanceSourceMaterial
import dev.ohs.fhir.model.r4.SubstanceSpecification
import dev.ohs.fhir.model.r4.SupplyDelivery
import dev.ohs.fhir.model.r4.SupplyRequest
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.model.r4.TerminologyCapabilities
import dev.ohs.fhir.model.r4.TestReport
import dev.ohs.fhir.model.r4.TestScript
import dev.ohs.fhir.model.r4.ValueSet
import dev.ohs.fhir.model.r4.VerificationResult
import dev.ohs.fhir.model.r4.VisionPrescription
import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Mirrors the R4 polymorphic resource registrations exposed by `FhirR4Json` in `fhir-model`.
 *
 * Keeping descriptor lookup in one place lets definition-based extraction work for every resource
 * type registered by the model library.
 */
internal object DefinitionExtractResourceRegistry {
  internal val supportedResourceTypes: Set<String> =
    setOf(
      "Account",
      "ActivityDefinition",
      "AdverseEvent",
      "AllergyIntolerance",
      "Appointment",
      "AppointmentResponse",
      "AuditEvent",
      "Basic",
      "Binary",
      "BiologicallyDerivedProduct",
      "BodyStructure",
      "Bundle",
      "CapabilityStatement",
      "CarePlan",
      "CareTeam",
      "CatalogEntry",
      "ChargeItem",
      "ChargeItemDefinition",
      "Claim",
      "ClaimResponse",
      "ClinicalImpression",
      "CodeSystem",
      "Communication",
      "CommunicationRequest",
      "CompartmentDefinition",
      "Composition",
      "ConceptMap",
      "Condition",
      "Consent",
      "Contract",
      "Coverage",
      "CoverageEligibilityRequest",
      "CoverageEligibilityResponse",
      "DetectedIssue",
      "Device",
      "DeviceDefinition",
      "DeviceMetric",
      "DeviceRequest",
      "DeviceUseStatement",
      "DiagnosticReport",
      "DocumentManifest",
      "DocumentReference",
      "EffectEvidenceSynthesis",
      "Encounter",
      "Endpoint",
      "EnrollmentRequest",
      "EnrollmentResponse",
      "EpisodeOfCare",
      "EventDefinition",
      "Evidence",
      "EvidenceVariable",
      "ExampleScenario",
      "ExplanationOfBenefit",
      "FamilyMemberHistory",
      "Flag",
      "Goal",
      "GraphDefinition",
      "Group",
      "GuidanceResponse",
      "HealthcareService",
      "ImagingStudy",
      "Immunization",
      "ImmunizationEvaluation",
      "ImmunizationRecommendation",
      "ImplementationGuide",
      "InsurancePlan",
      "Invoice",
      "Library",
      "Linkage",
      "List",
      "Location",
      "Measure",
      "MeasureReport",
      "Media",
      "Medication",
      "MedicationAdministration",
      "MedicationDispense",
      "MedicationKnowledge",
      "MedicationRequest",
      "MedicationStatement",
      "MedicinalProduct",
      "MedicinalProductAuthorization",
      "MedicinalProductContraindication",
      "MedicinalProductIndication",
      "MedicinalProductIngredient",
      "MedicinalProductInteraction",
      "MedicinalProductManufactured",
      "MedicinalProductPackaged",
      "MedicinalProductPharmaceutical",
      "MedicinalProductUndesirableEffect",
      "MessageDefinition",
      "MessageHeader",
      "MolecularSequence",
      "NamingSystem",
      "NutritionOrder",
      "Observation",
      "ObservationDefinition",
      "OperationDefinition",
      "OperationOutcome",
      "Organization",
      "OrganizationAffiliation",
      "Parameters",
      "Patient",
      "PaymentNotice",
      "PaymentReconciliation",
      "Person",
      "PlanDefinition",
      "Practitioner",
      "PractitionerRole",
      "Procedure",
      "Provenance",
      "Questionnaire",
      "QuestionnaireResponse",
      "RelatedPerson",
      "RequestGroup",
      "ResearchDefinition",
      "ResearchElementDefinition",
      "ResearchStudy",
      "ResearchSubject",
      "RiskAssessment",
      "RiskEvidenceSynthesis",
      "Schedule",
      "SearchParameter",
      "ServiceRequest",
      "Slot",
      "Specimen",
      "SpecimenDefinition",
      "StructureDefinition",
      "StructureMap",
      "Subscription",
      "Substance",
      "SubstanceNucleicAcid",
      "SubstancePolymer",
      "SubstanceProtein",
      "SubstanceReferenceInformation",
      "SubstanceSourceMaterial",
      "SubstanceSpecification",
      "SupplyDelivery",
      "SupplyRequest",
      "Task",
      "TerminologyCapabilities",
      "TestReport",
      "TestScript",
      "ValueSet",
      "VerificationResult",
      "VisionPrescription",
    )

  internal fun descriptorFor(resourceType: String): SerialDescriptor? =
    when (resourceType) {
      "Account" -> Account.serializer().descriptor

      "ActivityDefinition" -> ActivityDefinition.serializer().descriptor

      "AdverseEvent" -> AdverseEvent.serializer().descriptor

      "AllergyIntolerance" -> AllergyIntolerance.serializer().descriptor

      "Appointment" -> Appointment.serializer().descriptor

      "AppointmentResponse" -> AppointmentResponse.serializer().descriptor

      "AuditEvent" -> AuditEvent.serializer().descriptor

      "Basic" -> Basic.serializer().descriptor

      "Binary" -> Binary.serializer().descriptor

      "BiologicallyDerivedProduct" -> BiologicallyDerivedProduct.serializer().descriptor

      "BodyStructure" -> BodyStructure.serializer().descriptor

      "Bundle" -> Bundle.serializer().descriptor

      "CapabilityStatement" -> CapabilityStatement.serializer().descriptor

      "CarePlan" -> CarePlan.serializer().descriptor

      "CareTeam" -> CareTeam.serializer().descriptor

      "CatalogEntry" -> CatalogEntry.serializer().descriptor

      "ChargeItem" -> ChargeItem.serializer().descriptor

      "ChargeItemDefinition" -> ChargeItemDefinition.serializer().descriptor

      "Claim" -> Claim.serializer().descriptor

      "ClaimResponse" -> ClaimResponse.serializer().descriptor

      "ClinicalImpression" -> ClinicalImpression.serializer().descriptor

      "CodeSystem" -> CodeSystem.serializer().descriptor

      "Communication" -> Communication.serializer().descriptor

      "CommunicationRequest" -> CommunicationRequest.serializer().descriptor

      "CompartmentDefinition" -> CompartmentDefinition.serializer().descriptor

      "Composition" -> Composition.serializer().descriptor

      "ConceptMap" -> ConceptMap.serializer().descriptor

      "Condition" -> Condition.serializer().descriptor

      "Consent" -> Consent.serializer().descriptor

      "Contract" -> Contract.serializer().descriptor

      "Coverage" -> Coverage.serializer().descriptor

      "CoverageEligibilityRequest" -> CoverageEligibilityRequest.serializer().descriptor

      "CoverageEligibilityResponse" -> CoverageEligibilityResponse.serializer().descriptor

      "DetectedIssue" -> DetectedIssue.serializer().descriptor

      "Device" -> Device.serializer().descriptor

      "DeviceDefinition" -> DeviceDefinition.serializer().descriptor

      "DeviceMetric" -> DeviceMetric.serializer().descriptor

      "DeviceRequest" -> DeviceRequest.serializer().descriptor

      "DeviceUseStatement" -> DeviceUseStatement.serializer().descriptor

      "DiagnosticReport" -> DiagnosticReport.serializer().descriptor

      "DocumentManifest" -> DocumentManifest.serializer().descriptor

      "DocumentReference" -> DocumentReference.serializer().descriptor

      "EffectEvidenceSynthesis" -> EffectEvidenceSynthesis.serializer().descriptor

      "Encounter" -> Encounter.serializer().descriptor

      "Endpoint" -> Endpoint.serializer().descriptor

      "EnrollmentRequest" -> EnrollmentRequest.serializer().descriptor

      "EnrollmentResponse" -> EnrollmentResponse.serializer().descriptor

      "EpisodeOfCare" -> EpisodeOfCare.serializer().descriptor

      "EventDefinition" -> EventDefinition.serializer().descriptor

      "Evidence" -> Evidence.serializer().descriptor

      "EvidenceVariable" -> EvidenceVariable.serializer().descriptor

      "ExampleScenario" -> ExampleScenario.serializer().descriptor

      "ExplanationOfBenefit" -> ExplanationOfBenefit.serializer().descriptor

      "FamilyMemberHistory" -> FamilyMemberHistory.serializer().descriptor

      "Flag" -> Flag.serializer().descriptor

      "Goal" -> Goal.serializer().descriptor

      "GraphDefinition" -> GraphDefinition.serializer().descriptor

      "Group" -> Group.serializer().descriptor

      "GuidanceResponse" -> GuidanceResponse.serializer().descriptor

      "HealthcareService" -> HealthcareService.serializer().descriptor

      "ImagingStudy" -> ImagingStudy.serializer().descriptor

      "Immunization" -> Immunization.serializer().descriptor

      "ImmunizationEvaluation" -> ImmunizationEvaluation.serializer().descriptor

      "ImmunizationRecommendation" -> ImmunizationRecommendation.serializer().descriptor

      "ImplementationGuide" -> ImplementationGuide.serializer().descriptor

      "InsurancePlan" -> InsurancePlan.serializer().descriptor

      "Invoice" -> Invoice.serializer().descriptor

      "Library" -> Library.serializer().descriptor

      "Linkage" -> Linkage.serializer().descriptor

      "List" -> List.serializer().descriptor

      "Location" -> Location.serializer().descriptor

      "Measure" -> Measure.serializer().descriptor

      "MeasureReport" -> MeasureReport.serializer().descriptor

      "Media" -> Media.serializer().descriptor

      "Medication" -> Medication.serializer().descriptor

      "MedicationAdministration" -> MedicationAdministration.serializer().descriptor

      "MedicationDispense" -> MedicationDispense.serializer().descriptor

      "MedicationKnowledge" -> MedicationKnowledge.serializer().descriptor

      "MedicationRequest" -> MedicationRequest.serializer().descriptor

      "MedicationStatement" -> MedicationStatement.serializer().descriptor

      "MedicinalProduct" -> MedicinalProduct.serializer().descriptor

      "MedicinalProductAuthorization" -> MedicinalProductAuthorization.serializer().descriptor

      "MedicinalProductContraindication" -> MedicinalProductContraindication.serializer().descriptor

      "MedicinalProductIndication" -> MedicinalProductIndication.serializer().descriptor

      "MedicinalProductIngredient" -> MedicinalProductIngredient.serializer().descriptor

      "MedicinalProductInteraction" -> MedicinalProductInteraction.serializer().descriptor

      "MedicinalProductManufactured" -> MedicinalProductManufactured.serializer().descriptor

      "MedicinalProductPackaged" -> MedicinalProductPackaged.serializer().descriptor

      "MedicinalProductPharmaceutical" -> MedicinalProductPharmaceutical.serializer().descriptor

      "MedicinalProductUndesirableEffect" ->
        MedicinalProductUndesirableEffect.serializer().descriptor

      "MessageDefinition" -> MessageDefinition.serializer().descriptor

      "MessageHeader" -> MessageHeader.serializer().descriptor

      "MolecularSequence" -> MolecularSequence.serializer().descriptor

      "NamingSystem" -> NamingSystem.serializer().descriptor

      "NutritionOrder" -> NutritionOrder.serializer().descriptor

      "Observation" -> Observation.serializer().descriptor

      "ObservationDefinition" -> ObservationDefinition.serializer().descriptor

      "OperationDefinition" -> OperationDefinition.serializer().descriptor

      "OperationOutcome" -> OperationOutcome.serializer().descriptor

      "Organization" -> Organization.serializer().descriptor

      "OrganizationAffiliation" -> OrganizationAffiliation.serializer().descriptor

      "Parameters" -> Parameters.serializer().descriptor

      "Patient" -> Patient.serializer().descriptor

      "PaymentNotice" -> PaymentNotice.serializer().descriptor

      "PaymentReconciliation" -> PaymentReconciliation.serializer().descriptor

      "Person" -> Person.serializer().descriptor

      "PlanDefinition" -> PlanDefinition.serializer().descriptor

      "Practitioner" -> Practitioner.serializer().descriptor

      "PractitionerRole" -> PractitionerRole.serializer().descriptor

      "Procedure" -> Procedure.serializer().descriptor

      "Provenance" -> Provenance.serializer().descriptor

      "Questionnaire" -> Questionnaire.serializer().descriptor

      "QuestionnaireResponse" -> QuestionnaireResponse.serializer().descriptor

      "RelatedPerson" -> RelatedPerson.serializer().descriptor

      "RequestGroup" -> RequestGroup.serializer().descriptor

      "ResearchDefinition" -> ResearchDefinition.serializer().descriptor

      "ResearchElementDefinition" -> ResearchElementDefinition.serializer().descriptor

      "ResearchStudy" -> ResearchStudy.serializer().descriptor

      "ResearchSubject" -> ResearchSubject.serializer().descriptor

      "RiskAssessment" -> RiskAssessment.serializer().descriptor

      "RiskEvidenceSynthesis" -> RiskEvidenceSynthesis.serializer().descriptor

      "Schedule" -> Schedule.serializer().descriptor

      "SearchParameter" -> SearchParameter.serializer().descriptor

      "ServiceRequest" -> ServiceRequest.serializer().descriptor

      "Slot" -> Slot.serializer().descriptor

      "Specimen" -> Specimen.serializer().descriptor

      "SpecimenDefinition" -> SpecimenDefinition.serializer().descriptor

      "StructureDefinition" -> StructureDefinition.serializer().descriptor

      "StructureMap" -> StructureMap.serializer().descriptor

      "Subscription" -> Subscription.serializer().descriptor

      "Substance" -> Substance.serializer().descriptor

      "SubstanceNucleicAcid" -> SubstanceNucleicAcid.serializer().descriptor

      "SubstancePolymer" -> SubstancePolymer.serializer().descriptor

      "SubstanceProtein" -> SubstanceProtein.serializer().descriptor

      "SubstanceReferenceInformation" -> SubstanceReferenceInformation.serializer().descriptor

      "SubstanceSourceMaterial" -> SubstanceSourceMaterial.serializer().descriptor

      "SubstanceSpecification" -> SubstanceSpecification.serializer().descriptor

      "SupplyDelivery" -> SupplyDelivery.serializer().descriptor

      "SupplyRequest" -> SupplyRequest.serializer().descriptor

      "Task" -> Task.serializer().descriptor

      "TerminologyCapabilities" -> TerminologyCapabilities.serializer().descriptor

      "TestReport" -> TestReport.serializer().descriptor

      "TestScript" -> TestScript.serializer().descriptor

      "ValueSet" -> ValueSet.serializer().descriptor

      "VerificationResult" -> VerificationResult.serializer().descriptor

      "VisionPrescription" -> VisionPrescription.serializer().descriptor

      else -> null
    }
}
