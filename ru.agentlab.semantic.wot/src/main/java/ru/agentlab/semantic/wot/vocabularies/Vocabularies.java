package ru.agentlab.semantic.wot.vocabularies;

import org.eclipse.rdf4j.model.IRI;

import static org.eclipse.rdf4j.model.util.Values.iri;

public class Vocabularies {
    public static final String WOT_EXT_IRI = "https://tdx.agentlab.ru/#";
    public static final String WOT_IRI = "https://www.w3.org/2019/wot/td#";
    public static final String SCHEMA = "http://schema.org/";

    public static final IRI PLACE = iri(SCHEMA, "Place");
    public static final IRI NAME = iri(SCHEMA, "name");
    public static final IRI ADDRESS_LOCALITY = iri(SCHEMA, "addressLocality");
    public static final IRI STREET_ADDRESS = iri(SCHEMA, "streetAddress");
    public static final IRI LONGITUDE = iri(SCHEMA, "longitude");
    public static final IRI LATITUDE = iri(SCHEMA, "latitude");
    public static final IRI LOCATION = iri(SCHEMA, "location");
    public static final IRI ADDRESS = iri(SCHEMA, "address");

    public static final IRI PROPERTY_AFFORDANCE = iri(WOT_IRI, "PropertyAffordance");
    public final static IRI ACTION_INVOCATION = iri(WOT_EXT_IRI, "ActionInvocation");
    public static final IRI DESCRIBED_BY = iri(WOT_EXT_IRI, "describedBy");
    public static final IRI HAS_PROPERTY_AFFORDANCE = iri(WOT_IRI, "hasPropertyAffordance");
    public static final IRI HAS_ACTION_AFFORDANCE = iri(WOT_IRI, "hasActionAffordance");
    public static final IRI DESCRIBED_BY_AFFORDANCE = iri(WOT_EXT_IRI, "describedByAffordance");
    public static final IRI HAS_THING_MODEL = iri(WOT_EXT_IRI, "hasThingModel");
    public static final IRI THING = iri(WOT_IRI, "Thing");

    public static final IRI HAS_INPUT = iri(WOT_EXT_IRI, "hasInput");
    public static final IRI HAS_OUTPUT = iri(WOT_EXT_IRI, "hasOutput");
    public static final IRI HAS_INPUT_SCHEMA = iri(WOT_IRI, "hasInputSchema");
    public static final IRI HAS_OUTPUT_SCHEMA = iri(WOT_IRI, "hasOutputSchema");

}
