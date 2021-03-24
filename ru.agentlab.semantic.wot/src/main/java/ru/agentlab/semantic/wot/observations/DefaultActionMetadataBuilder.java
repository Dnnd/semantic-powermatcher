package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import ru.agentlab.semantic.wot.observation.api.MetadataBuilder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.DESCRIBED_BY_AFFORDANCE;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.MODIFIED;

public class DefaultActionMetadataBuilder implements MetadataBuilder<DefaultActionMetadata> {

    private OffsetDateTime lastModified;
    private final List<IRI> types = new ArrayList<>();
    private IRI actionAffordance;
    private IRI invocationIRI;

    public DefaultActionMetadataBuilder(IRI invocationIRI) {
        this.invocationIRI = invocationIRI;
    }

    public DefaultActionMetadataBuilder() {
        this(null);
    }

    @Override
    public DefaultActionMetadataBuilder process(Statement st) {
        if (invocationIRI == null) {
            invocationIRI = (IRI) st.getSubject();
        } else if (!st.getSubject().equals(invocationIRI)) {
            return this;
        }
        if (st.getPredicate().equals(MODIFIED)) {
            lastModified = OffsetDateTime.parse(st.getObject().stringValue(), ISO_OFFSET_DATE_TIME);
        } else if (st.getPredicate().equals(DESCRIBED_BY_AFFORDANCE)) {
            actionAffordance = (IRI) st.getObject();
        } else if (st.getPredicate().equals(RDF.TYPE)) {
            types.add((IRI) st.getObject());
        }
        return this;
    }

    @Override
    public DefaultActionMetadata build() {
        return new DefaultActionMetadata(actionAffordance, invocationIRI, lastModified, types);
    }
}
