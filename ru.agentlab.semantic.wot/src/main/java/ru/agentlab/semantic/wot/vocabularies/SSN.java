package ru.agentlab.semantic.wot.vocabularies;

import org.eclipse.rdf4j.model.IRI;

import static org.eclipse.rdf4j.model.util.Values.iri;

public class SSN {
    public static final String SOSA = "http://www.w3.org/ns/sosa/";
    public static final String SSN = "http://www.w3.org/ns/ssn/";

    public static final IRI PROPERTY = iri(SSN, "Property");
    public static final IRI FOR_PROPERTY = iri(SSN, "forProperty");
    public static final IRI HAS_PROPERTY = iri(SSN, "hasProperty");

    public static final IRI OBSERVATION = iri(SOSA, "Observation");

    public static final IRI MADE_BY_SENSOR = iri(SOSA, "madeBySensor");
    public static final IRI MADE_BY_SAMPLER = iri(SOSA, "madeBySampler");
    public static final IRI MADE_BY_ACTUATOR = iri(SOSA, "madeByActuator");

    public static final IRI HAS_SIMPLE_RESULT = iri(SOSA, "hasSimpleResult");
    public static final IRI HAS_RESULT = iri(SOSA, "hasResult");
    public static final IRI HAS_FEATURE_OF_INTEREST = iri(SOSA, "hasFeatureOfInterest");
    public static final IRI RESULT_TIME = iri(SOSA, "resultTime");
    public static final IRI OBSERVED_PROPERTY = iri(SOSA, "observedProperty");
}
