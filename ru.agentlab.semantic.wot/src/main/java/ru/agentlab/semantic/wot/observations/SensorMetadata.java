package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import ru.agentlab.semantic.wot.api.Metadata;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static ru.agentlab.semantic.wot.vocabularies.SSN.*;

public class SensorMetadata implements Metadata<SensorMetadata> {
    private final IRI affordanceIRI;
    private final IRI subjectIRI;
    private final IRI type;
    private final IRI thingIRI;
    private final IRI featureOfInterest;
    private final OffsetDateTime lastModified;

    public SensorMetadata(IRI affordanceIRI,
                          IRI subjectIRI,
                          IRI thingIRI,
                          OffsetDateTime lastModified,
                          IRI type,
                          IRI featureOfInterest
    ) {
        this.affordanceIRI = affordanceIRI;
        this.subjectIRI = subjectIRI;
        this.lastModified = lastModified;
        this.thingIRI = thingIRI;
        this.type = type;
        this.featureOfInterest = featureOfInterest;
    }

    @Override
    public Model toModel(Resource... context) {
        var lastModifiedLiteral = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(getLastModified());
        Model model = new LinkedHashModel();
        model.add(getIRI(), RDF.TYPE, getType(), context);
        model.add(getIRI(), MADE_BY_SENSOR, getAffordanceIRI(), context);
        model.add(getIRI(), RESULT_TIME, Values.literal(lastModifiedLiteral, XSD.DATETIME), context);
        model.add(getIRI(), HAS_FEATURE_OF_INTEREST, Values.literal(lastModifiedLiteral, XSD.DATETIME), context);
        return model;
    }

    @Override
    public IRI getIRI() {
        return subjectIRI;
    }

    @Override
    public IRI getAffordanceIRI() {
        return affordanceIRI;
    }

    @Override
    public IRI getThingIRI() {
        return thingIRI;
    }

    @Override
    public OffsetDateTime getLastModified() {
        return lastModified;
    }

    @Override
    public IRI getType() {
        return type;
    }

    public IRI getFeatureOfInterest() {
        return featureOfInterest;
    }
}
