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

import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Mirrors the R4 polymorphic resource registrations exposed by `FhirR4Json` in `fhir-model`.
 *
 * Keeping descriptor lookup in one place lets definition-based extraction work for every resource
 * type registered by the model library
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
      "Account" -> dev.ohs.fhir.model.r4.Account.serializer().descriptor

      "ActivityDefinition" -> dev.ohs.fhir.model.r4.ActivityDefinition.serializer().descriptor

      "AdverseEvent" -> dev.ohs.fhir.model.r4.AdverseEvent.serializer().descriptor

      "AllergyIntolerance" -> dev.ohs.fhir.model.r4.AllergyIntolerance.serializer().descriptor

      "Appointment" -> dev.ohs.fhir.model.r4.Appointment.serializer().descriptor

      "AppointmentResponse" -> dev.ohs.fhir.model.r4.AppointmentResponse.serializer().descriptor

      "AuditEvent" -> dev.ohs.fhir.model.r4.AuditEvent.serializer().descriptor

      "Basic" -> dev.ohs.fhir.model.r4.Basic.serializer().descriptor

      "Binary" -> dev.ohs.fhir.model.r4.Binary.serializer().descriptor

      "BiologicallyDerivedProduct" ->
        dev.ohs.fhir.model.r4.BiologicallyDerivedProduct.serializer().descriptor

      "BodyStructure" -> dev.ohs.fhir.model.r4.BodyStructure.serializer().descriptor

      "Bundle" -> dev.ohs.fhir.model.r4.Bundle.serializer().descriptor

      "CapabilityStatement" -> dev.ohs.fhir.model.r4.CapabilityStatement.serializer().descriptor

      "CarePlan" -> dev.ohs.fhir.model.r4.CarePlan.serializer().descriptor

      "CareTeam" -> dev.ohs.fhir.model.r4.CareTeam.serializer().descriptor

      "CatalogEntry" -> dev.ohs.fhir.model.r4.CatalogEntry.serializer().descriptor

      "ChargeItem" -> dev.ohs.fhir.model.r4.ChargeItem.serializer().descriptor

      "ChargeItemDefinition" -> dev.ohs.fhir.model.r4.ChargeItemDefinition.serializer().descriptor

      "Claim" -> dev.ohs.fhir.model.r4.Claim.serializer().descriptor

      "ClaimResponse" -> dev.ohs.fhir.model.r4.ClaimResponse.serializer().descriptor

      "ClinicalImpression" -> dev.ohs.fhir.model.r4.ClinicalImpression.serializer().descriptor

      "CodeSystem" -> dev.ohs.fhir.model.r4.CodeSystem.serializer().descriptor

      "Communication" -> dev.ohs.fhir.model.r4.Communication.serializer().descriptor

      "CommunicationRequest" -> dev.ohs.fhir.model.r4.CommunicationRequest.serializer().descriptor

      "CompartmentDefinition" -> dev.ohs.fhir.model.r4.CompartmentDefinition.serializer().descriptor

      "Composition" -> dev.ohs.fhir.model.r4.Composition.serializer().descriptor

      "ConceptMap" -> dev.ohs.fhir.model.r4.ConceptMap.serializer().descriptor

      "Condition" -> dev.ohs.fhir.model.r4.Condition.serializer().descriptor

      "Consent" -> dev.ohs.fhir.model.r4.Consent.serializer().descriptor

      "Contract" -> dev.ohs.fhir.model.r4.Contract.serializer().descriptor

      "Coverage" -> dev.ohs.fhir.model.r4.Coverage.serializer().descriptor

      "CoverageEligibilityRequest" ->
        dev.ohs.fhir.model.r4.CoverageEligibilityRequest.serializer().descriptor

      "CoverageEligibilityResponse" ->
        dev.ohs.fhir.model.r4.CoverageEligibilityResponse.serializer().descriptor

      "DetectedIssue" -> dev.ohs.fhir.model.r4.DetectedIssue.serializer().descriptor

      "Device" -> dev.ohs.fhir.model.r4.Device.serializer().descriptor

      "DeviceDefinition" -> dev.ohs.fhir.model.r4.DeviceDefinition.serializer().descriptor

      "DeviceMetric" -> dev.ohs.fhir.model.r4.DeviceMetric.serializer().descriptor

      "DeviceRequest" -> dev.ohs.fhir.model.r4.DeviceRequest.serializer().descriptor

      "DeviceUseStatement" -> dev.ohs.fhir.model.r4.DeviceUseStatement.serializer().descriptor

      "DiagnosticReport" -> dev.ohs.fhir.model.r4.DiagnosticReport.serializer().descriptor

      "DocumentManifest" -> dev.ohs.fhir.model.r4.DocumentManifest.serializer().descriptor

      "DocumentReference" -> dev.ohs.fhir.model.r4.DocumentReference.serializer().descriptor

      "EffectEvidenceSynthesis" ->
        dev.ohs.fhir.model.r4.EffectEvidenceSynthesis.serializer().descriptor

      "Encounter" -> dev.ohs.fhir.model.r4.Encounter.serializer().descriptor

      "Endpoint" -> dev.ohs.fhir.model.r4.Endpoint.serializer().descriptor

      "EnrollmentRequest" -> dev.ohs.fhir.model.r4.EnrollmentRequest.serializer().descriptor

      "EnrollmentResponse" -> dev.ohs.fhir.model.r4.EnrollmentResponse.serializer().descriptor

      "EpisodeOfCare" -> dev.ohs.fhir.model.r4.EpisodeOfCare.serializer().descriptor

      "EventDefinition" -> dev.ohs.fhir.model.r4.EventDefinition.serializer().descriptor

      "Evidence" -> dev.ohs.fhir.model.r4.Evidence.serializer().descriptor

      "EvidenceVariable" -> dev.ohs.fhir.model.r4.EvidenceVariable.serializer().descriptor

      "ExampleScenario" -> dev.ohs.fhir.model.r4.ExampleScenario.serializer().descriptor

      "ExplanationOfBenefit" -> dev.ohs.fhir.model.r4.ExplanationOfBenefit.serializer().descriptor

      "FamilyMemberHistory" -> dev.ohs.fhir.model.r4.FamilyMemberHistory.serializer().descriptor

      "Flag" -> dev.ohs.fhir.model.r4.Flag.serializer().descriptor

      "Goal" -> dev.ohs.fhir.model.r4.Goal.serializer().descriptor

      "GraphDefinition" -> dev.ohs.fhir.model.r4.GraphDefinition.serializer().descriptor

      "Group" -> dev.ohs.fhir.model.r4.Group.serializer().descriptor

      "GuidanceResponse" -> dev.ohs.fhir.model.r4.GuidanceResponse.serializer().descriptor

      "HealthcareService" -> dev.ohs.fhir.model.r4.HealthcareService.serializer().descriptor

      "ImagingStudy" -> dev.ohs.fhir.model.r4.ImagingStudy.serializer().descriptor

      "Immunization" -> dev.ohs.fhir.model.r4.Immunization.serializer().descriptor

      "ImmunizationEvaluation" ->
        dev.ohs.fhir.model.r4.ImmunizationEvaluation.serializer().descriptor

      "ImmunizationRecommendation" ->
        dev.ohs.fhir.model.r4.ImmunizationRecommendation.serializer().descriptor

      "ImplementationGuide" -> dev.ohs.fhir.model.r4.ImplementationGuide.serializer().descriptor

      "InsurancePlan" -> dev.ohs.fhir.model.r4.InsurancePlan.serializer().descriptor

      "Invoice" -> dev.ohs.fhir.model.r4.Invoice.serializer().descriptor

      "Library" -> dev.ohs.fhir.model.r4.Library.serializer().descriptor

      "Linkage" -> dev.ohs.fhir.model.r4.Linkage.serializer().descriptor

      "List" -> dev.ohs.fhir.model.r4.List.serializer().descriptor

      "Location" -> dev.ohs.fhir.model.r4.Location.serializer().descriptor

      "Measure" -> dev.ohs.fhir.model.r4.Measure.serializer().descriptor

      "MeasureReport" -> dev.ohs.fhir.model.r4.MeasureReport.serializer().descriptor

      "Media" -> dev.ohs.fhir.model.r4.Media.serializer().descriptor

      "Medication" -> dev.ohs.fhir.model.r4.Medication.serializer().descriptor

      "MedicationAdministration" ->
        dev.ohs.fhir.model.r4.MedicationAdministration.serializer().descriptor

      "MedicationDispense" -> dev.ohs.fhir.model.r4.MedicationDispense.serializer().descriptor

      "MedicationKnowledge" -> dev.ohs.fhir.model.r4.MedicationKnowledge.serializer().descriptor

      "MedicationRequest" -> dev.ohs.fhir.model.r4.MedicationRequest.serializer().descriptor

      "MedicationStatement" -> dev.ohs.fhir.model.r4.MedicationStatement.serializer().descriptor

      "MedicinalProduct" -> dev.ohs.fhir.model.r4.MedicinalProduct.serializer().descriptor

      "MedicinalProductAuthorization" ->
        dev.ohs.fhir.model.r4.MedicinalProductAuthorization.serializer().descriptor

      "MedicinalProductContraindication" ->
        dev.ohs.fhir.model.r4.MedicinalProductContraindication.serializer().descriptor

      "MedicinalProductIndication" ->
        dev.ohs.fhir.model.r4.MedicinalProductIndication.serializer().descriptor

      "MedicinalProductIngredient" ->
        dev.ohs.fhir.model.r4.MedicinalProductIngredient.serializer().descriptor

      "MedicinalProductInteraction" ->
        dev.ohs.fhir.model.r4.MedicinalProductInteraction.serializer().descriptor

      "MedicinalProductManufactured" ->
        dev.ohs.fhir.model.r4.MedicinalProductManufactured.serializer().descriptor

      "MedicinalProductPackaged" ->
        dev.ohs.fhir.model.r4.MedicinalProductPackaged.serializer().descriptor

      "MedicinalProductPharmaceutical" ->
        dev.ohs.fhir.model.r4.MedicinalProductPharmaceutical.serializer().descriptor

      "MedicinalProductUndesirableEffect" ->
        dev.ohs.fhir.model.r4.MedicinalProductUndesirableEffect.serializer().descriptor

      "MessageDefinition" -> dev.ohs.fhir.model.r4.MessageDefinition.serializer().descriptor

      "MessageHeader" -> dev.ohs.fhir.model.r4.MessageHeader.serializer().descriptor

      "MolecularSequence" -> dev.ohs.fhir.model.r4.MolecularSequence.serializer().descriptor

      "NamingSystem" -> dev.ohs.fhir.model.r4.NamingSystem.serializer().descriptor

      "NutritionOrder" -> dev.ohs.fhir.model.r4.NutritionOrder.serializer().descriptor

      "Observation" -> dev.ohs.fhir.model.r4.Observation.serializer().descriptor

      "ObservationDefinition" -> dev.ohs.fhir.model.r4.ObservationDefinition.serializer().descriptor

      "OperationDefinition" -> dev.ohs.fhir.model.r4.OperationDefinition.serializer().descriptor

      "OperationOutcome" -> dev.ohs.fhir.model.r4.OperationOutcome.serializer().descriptor

      "Organization" -> dev.ohs.fhir.model.r4.Organization.serializer().descriptor

      "OrganizationAffiliation" ->
        dev.ohs.fhir.model.r4.OrganizationAffiliation.serializer().descriptor

      "Parameters" -> dev.ohs.fhir.model.r4.Parameters.serializer().descriptor

      "Patient" -> dev.ohs.fhir.model.r4.Patient.serializer().descriptor

      "PaymentNotice" -> dev.ohs.fhir.model.r4.PaymentNotice.serializer().descriptor

      "PaymentReconciliation" -> dev.ohs.fhir.model.r4.PaymentReconciliation.serializer().descriptor

      "Person" -> dev.ohs.fhir.model.r4.Person.serializer().descriptor

      "PlanDefinition" -> dev.ohs.fhir.model.r4.PlanDefinition.serializer().descriptor

      "Practitioner" -> dev.ohs.fhir.model.r4.Practitioner.serializer().descriptor

      "PractitionerRole" -> dev.ohs.fhir.model.r4.PractitionerRole.serializer().descriptor

      "Procedure" -> dev.ohs.fhir.model.r4.Procedure.serializer().descriptor

      "Provenance" -> dev.ohs.fhir.model.r4.Provenance.serializer().descriptor

      "Questionnaire" -> dev.ohs.fhir.model.r4.Questionnaire.serializer().descriptor

      "QuestionnaireResponse" -> dev.ohs.fhir.model.r4.QuestionnaireResponse.serializer().descriptor

      "RelatedPerson" -> dev.ohs.fhir.model.r4.RelatedPerson.serializer().descriptor

      "RequestGroup" -> dev.ohs.fhir.model.r4.RequestGroup.serializer().descriptor

      "ResearchDefinition" -> dev.ohs.fhir.model.r4.ResearchDefinition.serializer().descriptor

      "ResearchElementDefinition" ->
        dev.ohs.fhir.model.r4.ResearchElementDefinition.serializer().descriptor

      "ResearchStudy" -> dev.ohs.fhir.model.r4.ResearchStudy.serializer().descriptor

      "ResearchSubject" -> dev.ohs.fhir.model.r4.ResearchSubject.serializer().descriptor

      "RiskAssessment" -> dev.ohs.fhir.model.r4.RiskAssessment.serializer().descriptor

      "RiskEvidenceSynthesis" -> dev.ohs.fhir.model.r4.RiskEvidenceSynthesis.serializer().descriptor

      "Schedule" -> dev.ohs.fhir.model.r4.Schedule.serializer().descriptor

      "SearchParameter" -> dev.ohs.fhir.model.r4.SearchParameter.serializer().descriptor

      "ServiceRequest" -> dev.ohs.fhir.model.r4.ServiceRequest.serializer().descriptor

      "Slot" -> dev.ohs.fhir.model.r4.Slot.serializer().descriptor

      "Specimen" -> dev.ohs.fhir.model.r4.Specimen.serializer().descriptor

      "SpecimenDefinition" -> dev.ohs.fhir.model.r4.SpecimenDefinition.serializer().descriptor

      "StructureDefinition" -> dev.ohs.fhir.model.r4.StructureDefinition.serializer().descriptor

      "StructureMap" -> dev.ohs.fhir.model.r4.StructureMap.serializer().descriptor

      "Subscription" -> dev.ohs.fhir.model.r4.Subscription.serializer().descriptor

      "Substance" -> dev.ohs.fhir.model.r4.Substance.serializer().descriptor

      "SubstanceNucleicAcid" -> dev.ohs.fhir.model.r4.SubstanceNucleicAcid.serializer().descriptor

      "SubstancePolymer" -> dev.ohs.fhir.model.r4.SubstancePolymer.serializer().descriptor

      "SubstanceProtein" -> dev.ohs.fhir.model.r4.SubstanceProtein.serializer().descriptor

      "SubstanceReferenceInformation" ->
        dev.ohs.fhir.model.r4.SubstanceReferenceInformation.serializer().descriptor

      "SubstanceSourceMaterial" ->
        dev.ohs.fhir.model.r4.SubstanceSourceMaterial.serializer().descriptor

      "SubstanceSpecification" ->
        dev.ohs.fhir.model.r4.SubstanceSpecification.serializer().descriptor

      "SupplyDelivery" -> dev.ohs.fhir.model.r4.SupplyDelivery.serializer().descriptor

      "SupplyRequest" -> dev.ohs.fhir.model.r4.SupplyRequest.serializer().descriptor

      "Task" -> dev.ohs.fhir.model.r4.Task.serializer().descriptor

      "TerminologyCapabilities" ->
        dev.ohs.fhir.model.r4.TerminologyCapabilities.serializer().descriptor

      "TestReport" -> dev.ohs.fhir.model.r4.TestReport.serializer().descriptor

      "TestScript" -> dev.ohs.fhir.model.r4.TestScript.serializer().descriptor

      "ValueSet" -> dev.ohs.fhir.model.r4.ValueSet.serializer().descriptor

      "VerificationResult" -> dev.ohs.fhir.model.r4.VerificationResult.serializer().descriptor

      "VisionPrescription" -> dev.ohs.fhir.model.r4.VisionPrescription.serializer().descriptor

      else -> null
    }
}
