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
 * Generated from kotlin-fhir's FhirR4Json polymorphic Resource registrations in
 * dev.ohs.fhir:fhir-model:1.0.0-beta05.
 */
internal object GeneratedR4ResourceDescriptorRegistry {
  private val descriptorsByResourceType: Map<String, SerialDescriptor> by lazy {
    mapOf(
      "Account" to dev.ohs.fhir.model.r4.Account.serializer().descriptor,
      "ActivityDefinition" to dev.ohs.fhir.model.r4.ActivityDefinition.serializer().descriptor,
      "AdverseEvent" to dev.ohs.fhir.model.r4.AdverseEvent.serializer().descriptor,
      "AllergyIntolerance" to dev.ohs.fhir.model.r4.AllergyIntolerance.serializer().descriptor,
      "Appointment" to dev.ohs.fhir.model.r4.Appointment.serializer().descriptor,
      "AppointmentResponse" to dev.ohs.fhir.model.r4.AppointmentResponse.serializer().descriptor,
      "AuditEvent" to dev.ohs.fhir.model.r4.AuditEvent.serializer().descriptor,
      "Basic" to dev.ohs.fhir.model.r4.Basic.serializer().descriptor,
      "Binary" to dev.ohs.fhir.model.r4.Binary.serializer().descriptor,
      "BiologicallyDerivedProduct" to
        dev.ohs.fhir.model.r4.BiologicallyDerivedProduct.serializer().descriptor,
      "BodyStructure" to dev.ohs.fhir.model.r4.BodyStructure.serializer().descriptor,
      "Bundle" to dev.ohs.fhir.model.r4.Bundle.serializer().descriptor,
      "CapabilityStatement" to dev.ohs.fhir.model.r4.CapabilityStatement.serializer().descriptor,
      "CarePlan" to dev.ohs.fhir.model.r4.CarePlan.serializer().descriptor,
      "CareTeam" to dev.ohs.fhir.model.r4.CareTeam.serializer().descriptor,
      "CatalogEntry" to dev.ohs.fhir.model.r4.CatalogEntry.serializer().descriptor,
      "ChargeItem" to dev.ohs.fhir.model.r4.ChargeItem.serializer().descriptor,
      "ChargeItemDefinition" to dev.ohs.fhir.model.r4.ChargeItemDefinition.serializer().descriptor,
      "Claim" to dev.ohs.fhir.model.r4.Claim.serializer().descriptor,
      "ClaimResponse" to dev.ohs.fhir.model.r4.ClaimResponse.serializer().descriptor,
      "ClinicalImpression" to dev.ohs.fhir.model.r4.ClinicalImpression.serializer().descriptor,
      "CodeSystem" to dev.ohs.fhir.model.r4.CodeSystem.serializer().descriptor,
      "Communication" to dev.ohs.fhir.model.r4.Communication.serializer().descriptor,
      "CommunicationRequest" to dev.ohs.fhir.model.r4.CommunicationRequest.serializer().descriptor,
      "CompartmentDefinition" to
        dev.ohs.fhir.model.r4.CompartmentDefinition.serializer().descriptor,
      "Composition" to dev.ohs.fhir.model.r4.Composition.serializer().descriptor,
      "ConceptMap" to dev.ohs.fhir.model.r4.ConceptMap.serializer().descriptor,
      "Condition" to dev.ohs.fhir.model.r4.Condition.serializer().descriptor,
      "Consent" to dev.ohs.fhir.model.r4.Consent.serializer().descriptor,
      "Contract" to dev.ohs.fhir.model.r4.Contract.serializer().descriptor,
      "Coverage" to dev.ohs.fhir.model.r4.Coverage.serializer().descriptor,
      "CoverageEligibilityRequest" to
        dev.ohs.fhir.model.r4.CoverageEligibilityRequest.serializer().descriptor,
      "CoverageEligibilityResponse" to
        dev.ohs.fhir.model.r4.CoverageEligibilityResponse.serializer().descriptor,
      "DetectedIssue" to dev.ohs.fhir.model.r4.DetectedIssue.serializer().descriptor,
      "Device" to dev.ohs.fhir.model.r4.Device.serializer().descriptor,
      "DeviceDefinition" to dev.ohs.fhir.model.r4.DeviceDefinition.serializer().descriptor,
      "DeviceMetric" to dev.ohs.fhir.model.r4.DeviceMetric.serializer().descriptor,
      "DeviceRequest" to dev.ohs.fhir.model.r4.DeviceRequest.serializer().descriptor,
      "DeviceUseStatement" to dev.ohs.fhir.model.r4.DeviceUseStatement.serializer().descriptor,
      "DiagnosticReport" to dev.ohs.fhir.model.r4.DiagnosticReport.serializer().descriptor,
      "DocumentManifest" to dev.ohs.fhir.model.r4.DocumentManifest.serializer().descriptor,
      "DocumentReference" to dev.ohs.fhir.model.r4.DocumentReference.serializer().descriptor,
      "EffectEvidenceSynthesis" to
        dev.ohs.fhir.model.r4.EffectEvidenceSynthesis.serializer().descriptor,
      "Encounter" to dev.ohs.fhir.model.r4.Encounter.serializer().descriptor,
      "Endpoint" to dev.ohs.fhir.model.r4.Endpoint.serializer().descriptor,
      "EnrollmentRequest" to dev.ohs.fhir.model.r4.EnrollmentRequest.serializer().descriptor,
      "EnrollmentResponse" to dev.ohs.fhir.model.r4.EnrollmentResponse.serializer().descriptor,
      "EpisodeOfCare" to dev.ohs.fhir.model.r4.EpisodeOfCare.serializer().descriptor,
      "EventDefinition" to dev.ohs.fhir.model.r4.EventDefinition.serializer().descriptor,
      "Evidence" to dev.ohs.fhir.model.r4.Evidence.serializer().descriptor,
      "EvidenceVariable" to dev.ohs.fhir.model.r4.EvidenceVariable.serializer().descriptor,
      "ExampleScenario" to dev.ohs.fhir.model.r4.ExampleScenario.serializer().descriptor,
      "ExplanationOfBenefit" to dev.ohs.fhir.model.r4.ExplanationOfBenefit.serializer().descriptor,
      "FamilyMemberHistory" to dev.ohs.fhir.model.r4.FamilyMemberHistory.serializer().descriptor,
      "Flag" to dev.ohs.fhir.model.r4.Flag.serializer().descriptor,
      "Goal" to dev.ohs.fhir.model.r4.Goal.serializer().descriptor,
      "GraphDefinition" to dev.ohs.fhir.model.r4.GraphDefinition.serializer().descriptor,
      "Group" to dev.ohs.fhir.model.r4.Group.serializer().descriptor,
      "GuidanceResponse" to dev.ohs.fhir.model.r4.GuidanceResponse.serializer().descriptor,
      "HealthcareService" to dev.ohs.fhir.model.r4.HealthcareService.serializer().descriptor,
      "ImagingStudy" to dev.ohs.fhir.model.r4.ImagingStudy.serializer().descriptor,
      "Immunization" to dev.ohs.fhir.model.r4.Immunization.serializer().descriptor,
      "ImmunizationEvaluation" to
        dev.ohs.fhir.model.r4.ImmunizationEvaluation.serializer().descriptor,
      "ImmunizationRecommendation" to
        dev.ohs.fhir.model.r4.ImmunizationRecommendation.serializer().descriptor,
      "ImplementationGuide" to dev.ohs.fhir.model.r4.ImplementationGuide.serializer().descriptor,
      "InsurancePlan" to dev.ohs.fhir.model.r4.InsurancePlan.serializer().descriptor,
      "Invoice" to dev.ohs.fhir.model.r4.Invoice.serializer().descriptor,
      "Library" to dev.ohs.fhir.model.r4.Library.serializer().descriptor,
      "Linkage" to dev.ohs.fhir.model.r4.Linkage.serializer().descriptor,
      "List" to dev.ohs.fhir.model.r4.List.serializer().descriptor,
      "Location" to dev.ohs.fhir.model.r4.Location.serializer().descriptor,
      "Measure" to dev.ohs.fhir.model.r4.Measure.serializer().descriptor,
      "MeasureReport" to dev.ohs.fhir.model.r4.MeasureReport.serializer().descriptor,
      "Media" to dev.ohs.fhir.model.r4.Media.serializer().descriptor,
      "Medication" to dev.ohs.fhir.model.r4.Medication.serializer().descriptor,
      "MedicationAdministration" to
        dev.ohs.fhir.model.r4.MedicationAdministration.serializer().descriptor,
      "MedicationDispense" to dev.ohs.fhir.model.r4.MedicationDispense.serializer().descriptor,
      "MedicationKnowledge" to dev.ohs.fhir.model.r4.MedicationKnowledge.serializer().descriptor,
      "MedicationRequest" to dev.ohs.fhir.model.r4.MedicationRequest.serializer().descriptor,
      "MedicationStatement" to dev.ohs.fhir.model.r4.MedicationStatement.serializer().descriptor,
      "MedicinalProduct" to dev.ohs.fhir.model.r4.MedicinalProduct.serializer().descriptor,
      "MedicinalProductAuthorization" to
        dev.ohs.fhir.model.r4.MedicinalProductAuthorization.serializer().descriptor,
      "MedicinalProductContraindication" to
        dev.ohs.fhir.model.r4.MedicinalProductContraindication.serializer().descriptor,
      "MedicinalProductIndication" to
        dev.ohs.fhir.model.r4.MedicinalProductIndication.serializer().descriptor,
      "MedicinalProductIngredient" to
        dev.ohs.fhir.model.r4.MedicinalProductIngredient.serializer().descriptor,
      "MedicinalProductInteraction" to
        dev.ohs.fhir.model.r4.MedicinalProductInteraction.serializer().descriptor,
      "MedicinalProductManufactured" to
        dev.ohs.fhir.model.r4.MedicinalProductManufactured.serializer().descriptor,
      "MedicinalProductPackaged" to
        dev.ohs.fhir.model.r4.MedicinalProductPackaged.serializer().descriptor,
      "MedicinalProductPharmaceutical" to
        dev.ohs.fhir.model.r4.MedicinalProductPharmaceutical.serializer().descriptor,
      "MedicinalProductUndesirableEffect" to
        dev.ohs.fhir.model.r4.MedicinalProductUndesirableEffect.serializer().descriptor,
      "MessageDefinition" to dev.ohs.fhir.model.r4.MessageDefinition.serializer().descriptor,
      "MessageHeader" to dev.ohs.fhir.model.r4.MessageHeader.serializer().descriptor,
      "MolecularSequence" to dev.ohs.fhir.model.r4.MolecularSequence.serializer().descriptor,
      "NamingSystem" to dev.ohs.fhir.model.r4.NamingSystem.serializer().descriptor,
      "NutritionOrder" to dev.ohs.fhir.model.r4.NutritionOrder.serializer().descriptor,
      "Observation" to dev.ohs.fhir.model.r4.Observation.serializer().descriptor,
      "ObservationDefinition" to
        dev.ohs.fhir.model.r4.ObservationDefinition.serializer().descriptor,
      "OperationDefinition" to dev.ohs.fhir.model.r4.OperationDefinition.serializer().descriptor,
      "OperationOutcome" to dev.ohs.fhir.model.r4.OperationOutcome.serializer().descriptor,
      "Organization" to dev.ohs.fhir.model.r4.Organization.serializer().descriptor,
      "OrganizationAffiliation" to
        dev.ohs.fhir.model.r4.OrganizationAffiliation.serializer().descriptor,
      "Parameters" to dev.ohs.fhir.model.r4.Parameters.serializer().descriptor,
      "Patient" to dev.ohs.fhir.model.r4.Patient.serializer().descriptor,
      "PaymentNotice" to dev.ohs.fhir.model.r4.PaymentNotice.serializer().descriptor,
      "PaymentReconciliation" to
        dev.ohs.fhir.model.r4.PaymentReconciliation.serializer().descriptor,
      "Person" to dev.ohs.fhir.model.r4.Person.serializer().descriptor,
      "PlanDefinition" to dev.ohs.fhir.model.r4.PlanDefinition.serializer().descriptor,
      "Practitioner" to dev.ohs.fhir.model.r4.Practitioner.serializer().descriptor,
      "PractitionerRole" to dev.ohs.fhir.model.r4.PractitionerRole.serializer().descriptor,
      "Procedure" to dev.ohs.fhir.model.r4.Procedure.serializer().descriptor,
      "Provenance" to dev.ohs.fhir.model.r4.Provenance.serializer().descriptor,
      "Questionnaire" to dev.ohs.fhir.model.r4.Questionnaire.serializer().descriptor,
      "QuestionnaireResponse" to
        dev.ohs.fhir.model.r4.QuestionnaireResponse.serializer().descriptor,
      "RelatedPerson" to dev.ohs.fhir.model.r4.RelatedPerson.serializer().descriptor,
      "RequestGroup" to dev.ohs.fhir.model.r4.RequestGroup.serializer().descriptor,
      "ResearchDefinition" to dev.ohs.fhir.model.r4.ResearchDefinition.serializer().descriptor,
      "ResearchElementDefinition" to
        dev.ohs.fhir.model.r4.ResearchElementDefinition.serializer().descriptor,
      "ResearchStudy" to dev.ohs.fhir.model.r4.ResearchStudy.serializer().descriptor,
      "ResearchSubject" to dev.ohs.fhir.model.r4.ResearchSubject.serializer().descriptor,
      "RiskAssessment" to dev.ohs.fhir.model.r4.RiskAssessment.serializer().descriptor,
      "RiskEvidenceSynthesis" to
        dev.ohs.fhir.model.r4.RiskEvidenceSynthesis.serializer().descriptor,
      "Schedule" to dev.ohs.fhir.model.r4.Schedule.serializer().descriptor,
      "SearchParameter" to dev.ohs.fhir.model.r4.SearchParameter.serializer().descriptor,
      "ServiceRequest" to dev.ohs.fhir.model.r4.ServiceRequest.serializer().descriptor,
      "Slot" to dev.ohs.fhir.model.r4.Slot.serializer().descriptor,
      "Specimen" to dev.ohs.fhir.model.r4.Specimen.serializer().descriptor,
      "SpecimenDefinition" to dev.ohs.fhir.model.r4.SpecimenDefinition.serializer().descriptor,
      "StructureDefinition" to dev.ohs.fhir.model.r4.StructureDefinition.serializer().descriptor,
      "StructureMap" to dev.ohs.fhir.model.r4.StructureMap.serializer().descriptor,
      "Subscription" to dev.ohs.fhir.model.r4.Subscription.serializer().descriptor,
      "Substance" to dev.ohs.fhir.model.r4.Substance.serializer().descriptor,
      "SubstanceNucleicAcid" to dev.ohs.fhir.model.r4.SubstanceNucleicAcid.serializer().descriptor,
      "SubstancePolymer" to dev.ohs.fhir.model.r4.SubstancePolymer.serializer().descriptor,
      "SubstanceProtein" to dev.ohs.fhir.model.r4.SubstanceProtein.serializer().descriptor,
      "SubstanceReferenceInformation" to
        dev.ohs.fhir.model.r4.SubstanceReferenceInformation.serializer().descriptor,
      "SubstanceSourceMaterial" to
        dev.ohs.fhir.model.r4.SubstanceSourceMaterial.serializer().descriptor,
      "SubstanceSpecification" to
        dev.ohs.fhir.model.r4.SubstanceSpecification.serializer().descriptor,
      "SupplyDelivery" to dev.ohs.fhir.model.r4.SupplyDelivery.serializer().descriptor,
      "SupplyRequest" to dev.ohs.fhir.model.r4.SupplyRequest.serializer().descriptor,
      "Task" to dev.ohs.fhir.model.r4.Task.serializer().descriptor,
      "TerminologyCapabilities" to
        dev.ohs.fhir.model.r4.TerminologyCapabilities.serializer().descriptor,
      "TestReport" to dev.ohs.fhir.model.r4.TestReport.serializer().descriptor,
      "TestScript" to dev.ohs.fhir.model.r4.TestScript.serializer().descriptor,
      "ValueSet" to dev.ohs.fhir.model.r4.ValueSet.serializer().descriptor,
      "VerificationResult" to dev.ohs.fhir.model.r4.VerificationResult.serializer().descriptor,
      "VisionPrescription" to dev.ohs.fhir.model.r4.VisionPrescription.serializer().descriptor,
    )
  }

  fun descriptorFor(resourceType: String): SerialDescriptor? =
    descriptorsByResourceType[resourceType]
}
