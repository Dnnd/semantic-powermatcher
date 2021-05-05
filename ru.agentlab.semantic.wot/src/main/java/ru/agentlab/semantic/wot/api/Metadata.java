package ru.agentlab.semantic.wot.api;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static ru.agentlab.semantic.wot.vocabularies.SSN.RESULT_TIME;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.DESCRIBED_BY_AFFORDANCE;

public interface Metadata<M> {
    default Model toModel(Resource... context) {
        var lastModifiedLiteral = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(getLastModified());
        Model model = new LinkedHashModel();
        model.add(getIRI(), RDF.TYPE, getType(), context);
        model.add(getIRI(), DESCRIBED_BY_AFFORDANCE, getAffordanceIRI(), context);
        model.add(getIRI(), RESULT_TIME, Values.literal(lastModifiedLiteral, XSD.DATETIME), context);
        return model;
    }

    IRI getIRI();

    IRI getAffordanceIRI();

    IRI getThingIRI();

    OffsetDateTime getLastModified();

    IRI getType();

    default M unwrap() {
        return (M) this;
    }
}
