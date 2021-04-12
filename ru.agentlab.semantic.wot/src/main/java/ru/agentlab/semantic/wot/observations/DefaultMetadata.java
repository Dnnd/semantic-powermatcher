package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import ru.agentlab.semantic.wot.api.Metadata;

import java.time.OffsetDateTime;

public class DefaultMetadata implements Metadata<DefaultMetadata> {
    private final IRI affordanceIRI;
    private final IRI subjectIRI;
    private final IRI type;
    private final OffsetDateTime lastModified;

    public DefaultMetadata(IRI affordanceIRI, IRI subjectIRI, OffsetDateTime lastModified, IRI type) {
        this.affordanceIRI = affordanceIRI;
        this.subjectIRI = subjectIRI;
        this.lastModified = lastModified;
        this.type = type;
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
    public OffsetDateTime getLastModified() {
        return lastModified;
    }

    @Override
    public IRI getType() {
        return type;
    }
}
