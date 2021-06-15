package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import ru.agentlab.semantic.wot.api.MetadataParser;

import java.time.OffsetDateTime;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static ru.agentlab.semantic.wot.vocabularies.SSN.*;

public class SensorMetadataParser implements MetadataParser<SensorMetadata> {
    private OffsetDateTime lastModified;
    private IRI type;
    private IRI affordanceIRI;
    private IRI subjectIRI;
    private IRI thingIRI;
    private IRI featureOfInterest;

    public SensorMetadataParser(IRI subjectIRI) {
        this.subjectIRI = subjectIRI;
    }

    @Override
    public SensorMetadataParser process(Statement st) {
        if (subjectIRI == null) {
            subjectIRI = (IRI) st.getSubject();
        } else if (!st.getSubject().equals(subjectIRI)) {
            return this;
        }
        if (st.getPredicate().equals(RESULT_TIME)) {
            lastModified = OffsetDateTime.parse(st.getObject().stringValue(), ISO_OFFSET_DATE_TIME);
        } else if (st.getPredicate().equals(OBSERVED_PROPERTY)) {
            affordanceIRI = (IRI) st.getObject();
        } else if (st.getPredicate().equals(RDF.TYPE)) {
            type = (IRI) st.getObject();
        } else if (st.getPredicate().equals(MADE_BY_SENSOR) ||
                st.getPredicate().equals(MADE_BY_ACTUATOR) ||
                st.getPredicate().equals(MADE_BY_SAMPLER)) {
            thingIRI = (IRI) st.getObject();
        } else if (st.getPredicate().equals(HAS_FEATURE_OF_INTEREST)) {
            featureOfInterest = (IRI) st.getObject();
        }
        return this;
    }

    @Override
    public SensorMetadata build() {
        return new SensorMetadata(affordanceIRI, subjectIRI, thingIRI, lastModified, type, featureOfInterest);
    }
}
