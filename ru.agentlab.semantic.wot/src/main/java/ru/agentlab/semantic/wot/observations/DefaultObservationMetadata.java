package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;

import java.time.OffsetDateTime;

public class DefaultObservationMetadata {
    private final OffsetDateTime lastModified;
    private final IRI propertyAffordance;
    private final IRI observationIRI;

    public DefaultObservationMetadata(OffsetDateTime lastModified, IRI propertyAffordance, IRI observationIRI) {
        this.lastModified = lastModified;
        this.propertyAffordance = propertyAffordance;
        this.observationIRI = observationIRI;
    }


    public OffsetDateTime getLastModified() {
        return lastModified;
    }

    public IRI getPropertyAffordance() {
        return propertyAffordance;
    }

    public IRI getObservationIRI() {
        return observationIRI;
    }

    @Override
    public String toString() {
        return "DefaultObservationMetadata{" +
                "lastModified=" + lastModified +
                ", propertyAffordance=" + propertyAffordance +
                ", observationIRI=" + observationIRI +
                '}';
    }
}
