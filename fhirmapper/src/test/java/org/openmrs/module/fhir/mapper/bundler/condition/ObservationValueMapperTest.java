package org.openmrs.module.fhir.mapper.bundler.condition;

import org.hl7.fhir.instance.model.CodeableConcept;
import org.hl7.fhir.instance.model.DateAndTime;
import org.hl7.fhir.instance.model.Decimal;
import org.hl7.fhir.instance.model.String_;
import org.hl7.fhir.instance.model.Type;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
public class ObservationValueMapperTest extends BaseModuleWebContextSensitiveTest {
    @Autowired
    ConceptService conceptService;

    @Autowired
    ObservationValueMapper observationValueMapper;

    @Test
    public void shouldMapDateValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Date"));
        obs.setConcept(concept);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date obsDate = dateFormat.parse("2014-03-12");
        obs.setValueDate(obsDate);
        Type value = observationValueMapper.map(obs);
        assertTrue(value instanceof org.hl7.fhir.instance.model.Date);
        DateAndTime date = ((org.hl7.fhir.instance.model.Date) value).getValue();
        java.util.Date actualDate = DateUtil.parseDate(date.toString());
        assertEquals(obsDate, actualDate);
    }

    @Test
    public void shouldMapNumericValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Numeric"));
        obs.setConcept(concept);
        double valueNumeric = 10.0;
        obs.setValueNumeric(valueNumeric);
        Type value = observationValueMapper.map(obs);
        assertTrue(value instanceof Decimal);
        double mappedValue = ((Decimal) value).getValue().doubleValue();
        assertTrue(mappedValue == valueNumeric);
    }

    @Test
    public void shouldMapTextValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Text"));
        obs.setConcept(concept);
        String valueText = "Hello";
        obs.setValueText(valueText);
        Type value = observationValueMapper.map(obs);
        assertTrue(value instanceof String_);
        assertEquals(valueText, ((String_) value).getValue());
    }

    @Test
    public void shouldMapCodedValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Coded"));
        obs.setConcept(concept);
        Concept codedConcept = new Concept(10);
        String conceptName = "Concept";
        codedConcept.addName(new ConceptName(conceptName, conceptService.getLocalesOfConceptNames().iterator().next()));
        obs.setValueCoded(codedConcept);
        Type value = observationValueMapper.map(obs);
        assertTrue(value instanceof CodeableConcept);
        assertEquals(conceptName, ((CodeableConcept) value).getCoding().get(0).getDisplaySimple());
    }
}