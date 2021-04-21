package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;

import static ru.agentlab.semantic.wot.services.repositories.WotServicesVocabulary.*;

public class ThingServiceImplementation {

    private final IRI modelIRI;
    private final String configurationID;
    private final String bundleID;
    private final ServiceType serviceType;

    public ThingServiceImplementation(IRI modelIRI, String configurationID, String bundleID, ServiceType serviceType) {
        this.modelIRI = modelIRI;
        this.configurationID = configurationID;
        this.bundleID = bundleID;
        this.serviceType = serviceType;
    }

    public IRI getModelIRI() {
        return modelIRI;
    }

    public String getConfigurationID() {
        return configurationID;
    }

    public String getBundleID() {
        return bundleID;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public static class Builder {
        private IRI modelIRI;
        private String configurationID;
        private String bundleID;
        private ServiceType serviceType;

        public Builder processStatement(Statement statement) {
            var pred = statement.getPredicate();
            if (pred.equals(CONFIGURATION_ID)) {
                configurationID = statement.getObject().stringValue();
            } else if (pred.equals(BUNDLE_ID)) {
                bundleID = statement.getObject().stringValue();
            } else if (pred.equals(MODEL_IRI)) {
                modelIRI = (IRI) statement.getObject();
            } else if (pred.equals(SERVICE_TYPE) && statement.getObject().equals(FACTORY)) {
                serviceType = ServiceType.FACTORY;
            } else if (pred.equals(SERVICE_TYPE) && statement.getObject().equals(SINGLETON)) {
                serviceType = ServiceType.SINGLETON;
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

        public Builder setConfigurationID(String configurationID) {
            this.configurationID = configurationID;
            return this;
        }

        public Builder setBundleID(String bundleID) {
            this.bundleID = bundleID;
            return this;
        }

        public Builder setServiceType(ServiceType serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        public ThingServiceImplementation build() {
            return new ThingServiceImplementation(modelIRI, configurationID, bundleID, serviceType);
        }
    }
}
