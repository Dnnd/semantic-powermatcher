package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;

import static ru.agentlab.semantic.wot.services.repositories.WOT_SERVICES.*;

public class ThingServiceImplementation {
    private final IRI modelIRI;
    private final IRI configurationID;
    private final IRI bundleID;

    public ThingServiceImplementation(IRI modelIRI, IRI configurationID, IRI bundleID) {
        this.modelIRI = modelIRI;
        this.configurationID = configurationID;
        this.bundleID = bundleID;
    }

    public IRI getModelIRI() {
        return modelIRI;
    }

    public IRI getConfigurationID() {
        return configurationID;
    }

    public IRI getBundleID() {
        return bundleID;
    }

    public static class Builder {
        private IRI modelIRI;
        private IRI configurationID;
        private IRI bundleID;

        public Builder processStatement(Statement statement) {
            var pred = statement.getPredicate();
            if (pred.equals(CONFIGURATION_ID)) {
                configurationID = (IRI) statement.getObject();
            } else if (pred.equals(BUNDLE_ID)) {
                bundleID = (IRI) statement.getObject();
            } else if (pred.equals(MODEL_IRI)) {
                modelIRI = (IRI) statement.getObject();
            }
            return this;
        }

        public Builder setModel(Model model) {
            model.forEach(this::processStatement);
            return this;
        }

        public Builder setModelIRI(IRI modelIRI) {
            this.modelIRI = modelIRI;
            return this;
        }

        public Builder setConfigurationID(IRI configurationID) {
            this.configurationID = configurationID;
            return this;
        }

        public Builder setBundleID(IRI bundleID) {
            this.bundleID = bundleID;
            return this;
        }

        public ThingServiceImplementation build() {
            return new ThingServiceImplementation(modelIRI, configurationID, bundleID);
        }
    }
}
