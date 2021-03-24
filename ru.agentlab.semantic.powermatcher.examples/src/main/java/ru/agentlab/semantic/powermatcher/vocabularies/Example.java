package ru.agentlab.semantic.powermatcher.vocabularies;

import org.eclipse.rdf4j.model.IRI;

import static org.eclipse.rdf4j.model.util.Values.iri;

public class Example {
    public final static String EXAMPLE_IRI = "https://example.agentlab.ru/#";
    public final static IRI POWER = iri(EXAMPLE_IRI, "Power");
    public final static IRI TEMPERATURE = iri(EXAMPLE_IRI, "Temperature");
    public final static IRI OUTSIDE = iri(EXAMPLE_IRI, "Outside");
    public final static IRI INSIDE = iri(EXAMPLE_IRI, "Inside");
    public final static IRI LOCATION_TYPE = iri(EXAMPLE_IRI, "locationType");
    public final static IRI WIDTH = iri(EXAMPLE_IRI, "width");
    public final static IRI HEIGHT = iri(EXAMPLE_IRI, "height");
    public final static IRI LENGTH = iri(EXAMPLE_IRI, "length");
}
