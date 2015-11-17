package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.DiagnosticReport;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.valueset.ObservationStatusEnum;
import ca.uhn.fhir.model.primitive.DateTimeDt;
import org.openmrs.Obs;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.openmrs.module.fhir.MRSProperties.*;

@Component("testResultMapper")
public class TestResultMapper implements EmrObsResourceHandler {

    @Autowired
    private ObservationMapper observationMapper;

    @Autowired
    private IdMappingsRepository idMappingsRepository;

    @Autowired
    private DiagnosticReportBuilder diagnosticReportBuilder;

    @Autowired
    private CodeableConceptService codeableConceptService;

    @Override
    public boolean canHandle(Obs observation) {
        return MRS_ENC_TYPE_LAB_RESULT.equals(observation.getEncounter().getEncounterType().getName());
    }

    @Override
    public List<FHIRResource> map(Obs topLevelObs, Encounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> FHIRResourceList = new ArrayList<>();
        if (topLevelObs != null) {
            if (!isPanel(topLevelObs)) {
                buildTestResult(topLevelObs, fhirEncounter, FHIRResourceList, systemProperties);
            } else {
                for (Obs testObsGroup : topLevelObs.getGroupMembers()) {
                    buildTestResult(testObsGroup, fhirEncounter, FHIRResourceList, systemProperties);
                }
            }
        }
        return FHIRResourceList;
    }

    private Boolean isPanel(Obs obs) {
        return obs.getConcept().getConceptClass().getName().equals(MRS_CONCEPT_CLASS_LAB_SET);
    }

    private void buildTestResult(Obs topLevelTestObs, Encounter fhirEncounter, List<FHIRResource> fhirResourceList, SystemProperties systemProperties) {
        DiagnosticReport diagnosticReport = build(topLevelTestObs, fhirEncounter, systemProperties);
        if (diagnosticReport != null) {
            for (Obs resultObsGroup : topLevelTestObs.getGroupMembers()) {
                FHIRResource resultResource = getResultResource(resultObsGroup, fhirEncounter, systemProperties);
                if (resultResource == null) return;
                ResourceReferenceDt resourceReference = diagnosticReport.addResult();
                resourceReference.setReference(resultResource.getIdentifier().getValue());
                fhirResourceList.add(resultResource);

            }
            FHIRResource FHIRResource = new FHIRResource("Diagnostic Report", diagnosticReport.getIdentifier(), diagnosticReport);
            fhirResourceList.add(FHIRResource);
        }
    }

    private FHIRResource getResultResource(Obs resultObsGroup, Encounter fhirEncounter, SystemProperties systemProperties) {
        CompoundObservation resultGroupObservation = new CompoundObservation(resultObsGroup);
        FHIRResource observationResource = getResultObservation(resultObsGroup, fhirEncounter, systemProperties, resultGroupObservation);

        Observation fhirObservation = (Observation) observationResource.getResource();
        setObservationStatusForResult(fhirObservation);
        setObservationNotes(resultGroupObservation, fhirObservation);

        return observationResource;
    }

    private void setObservationNotes(CompoundObservation resultGroupObservation, Observation fhirObservation) {
        Obs labNotesObs = resultGroupObservation.getMemberObsForConceptName(MRS_CONCEPT_NAME_LAB_NOTES);
        if (null != labNotesObs && null != labNotesObs.getValueText())
            fhirObservation.setComments(labNotesObs.getValueText());
    }

    private void setObservationStatusForResult(Observation resultObservation) {
        resultObservation.setStatus(ObservationStatusEnum.FINAL);
    }

    private FHIRResource getResultObservation(Obs resultObsGroup, Encounter fhirEncounter, SystemProperties systemProperties, CompoundObservation resultGroupObservation) {
        Obs resultObs = resultGroupObservation.getMemberObsForConcept(resultObsGroup.getConcept());
        if(null != resultObs)
            return observationMapper.mapObservation(resultObs, fhirEncounter, systemProperties);
        return observationMapper.buildObservationResource(fhirEncounter,systemProperties,UUID.randomUUID().toString(),fhirEncounter.getResourceName());
    }

    private DiagnosticReport build(Obs obs, Encounter fhirEncounter, SystemProperties systemProperties) {
        DiagnosticReport report = diagnosticReportBuilder.build(obs, fhirEncounter, systemProperties);
        CodeableConceptDt name = codeableConceptService.addTRCoding(obs.getConcept(), idMappingsRepository);
        if (name.getCoding() != null && name.getCoding().isEmpty()) {
            return null;
        }
        report.setCode(name);
        org.openmrs.Order obsOrder = obs.getOrder();
        report.setEffective(getOrderTime(obsOrder));

        String uuid = obsOrder.getEncounter().getUuid();
        IdMapping encounterIdMapping = idMappingsRepository.findByInternalId(uuid);
        if (encounterIdMapping == null) {
            throw new RuntimeException("Encounter id [" + uuid + "] doesn't have id mapping.");
        }

        report.addRequest().setReference(encounterIdMapping.getUri());

        return report;
    }

    private DateTimeDt getOrderTime(org.openmrs.Order obsOrder) {
        DateTimeDt diagnostic = new DateTimeDt();
        diagnostic.setValue(obsOrder.getDateActivated(), TemporalPrecisionEnum.MILLI);
        return diagnostic;
    }
}
