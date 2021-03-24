package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;

import java.time.OffsetDateTime;
import java.util.List;

public class DefaultActionMetadata {
    private final IRI actionAffordance;
    private final IRI actionInvocation;
    private final OffsetDateTime lastModified;
    private final List<IRI> types;

    public DefaultActionMetadata(IRI actionAffordance, IRI actionInvocation, OffsetDateTime lastModified, List<IRI> types) {
        this.actionAffordance = actionAffordance;
        this.lastModified = lastModified;
        this.types = types;
        this.actionInvocation = actionInvocation;
    }

    public IRI getActionInvocation() {
        return actionInvocation;
    }
    public IRI getActionAffordance() {
        return actionAffordance;
    }

    public OffsetDateTime getLastModified() {
        return lastModified;
    }

    public List<IRI> getTypes() {
        return types;
    }

    @Override
    public String toString() {
        return "DefaultActionMetadata{" +
                "actionAffordance=" + actionAffordance +
                ", actionInvocation=" + actionInvocation +
                ", lastModified=" + lastModified +
                ", types=" + types +
                '}';
    }
}
