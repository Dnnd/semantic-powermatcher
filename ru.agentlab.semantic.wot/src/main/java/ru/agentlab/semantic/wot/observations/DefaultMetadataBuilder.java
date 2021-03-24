package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import ru.agentlab.semantic.wot.observation.api.MetadataBuilder;

import java.time.OffsetDateTime;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

public class DefaultMetadataBuilder implements MetadataBuilder<DefaultObservationMetadata> {
    private OffsetDateTime lastModified;
    private IRI propertyAffordance;
    private IRI observationIRI;

    public DefaultMetadataBuilder(IRI observationIRI) {
        this.observationIRI = observationIRI;
    }

    public DefaultMetadataBuilder() {
        this(null);
    }

    @Override
    public DefaultMetadataBuilder process(Statement st) {
        if (observationIRI == null) {
            observationIRI = (IRI) st.getSubject();
        } else if (!st.getSubject().equals(observationIRI)) {
            return this;
        }
        if (st.getPredicate().equals(MODIFIED)) {
            lastModified = OffsetDateTime.parse(st.getObject().stringValue(), ISO_OFFSET_DATE_TIME);
        } else if (st.getPredicate().equals(DESCRIBED_BY_AFFORDANCE)) {
            propertyAffordance = (IRI) st.getObject();
        }
        return this;
    }

    @Override
    public DefaultObservationMetadata build() {
        return new DefaultObservationMetadata(lastModified, propertyAffordance, observationIRI);
    }
}
