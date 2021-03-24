package ru.agentlab.semantic.wot.vocabularies;

import org.eclipse.rdf4j.model.IRI;

import static org.eclipse.rdf4j.model.util.Values.iri;

public class Vocabularies {
    public static final String WOT_EXT_IRI = "https://tdx.agentlab.ru/#";
    public static final String WOT_IRI = "https://www.w3.org/2019/wot/td#";

    public static final IRI PROPERTY_AFFORDANCE = iri(WOT_IRI, "PropertyAffordance");
    public static final IRI PROPERTY_STATE = iri(WOT_EXT_IRI, "PropertyState");
    public static final IRI HAS_PROPERTY_AFFORDANCE = iri(WOT_IRI, "hasPropertyAffordance");
    public static final IRI HAS_ACTION_AFFORDANCE = iri(WOT_IRI, "hasActionAffordance");
    public static final IRI MODIFIED = iri(WOT_EXT_IRI, "modified");
    public static final IRI DESCRIBED_BY_AFFORDANCE = iri(WOT_EXT_IRI, "describedByAffordance");
    public static final IRI HAS_VALUE = iri(WOT_EXT_IRI, "hasValue");
    public static final IRI HAS_INPUT = iri(WOT_EXT_IRI, "hasInput");
    public static final IRI HAS_OUTPUT = iri(WOT_EXT_IRI, "hasOutput");
    public static final IRI HAS_INPUT_SCHEMA = iri(WOT_IRI, "hasInputSchema");
    public static final IRI HAS_OUTPUT_SCHEMA = iri(WOT_IRI, "hasOutputSchema");
}
