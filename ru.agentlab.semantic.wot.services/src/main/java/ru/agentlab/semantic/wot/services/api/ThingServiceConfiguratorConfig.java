package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;

import java.util.Objects;

import static ru.agentlab.semantic.wot.services.api.WotServicesVocabulary.*;

public class ThingServiceConfiguratorConfig {

    private final IRI modelIRI;
    private final IRI configuratorIRI;
    private final String configuratorConfPid;
    private final String configuratorBundleID;
    private final ServiceType configuratorServiceType;

    public ThingServiceConfiguratorConfig(
            IRI configuratorIRI,
            IRI modelIRI,
            String configuratorConfPid,
            String configuratorBundleID,
            ServiceType configuratorServiceType) {

        this.modelIRI = modelIRI;
        this.configuratorIRI = configuratorIRI;
        this.configuratorBundleID = configuratorBundleID;
        this.configuratorConfPid = configuratorConfPid;
        this.configuratorServiceType = configuratorServiceType;
    }

    public IRI getConfiguratorIRI() {
        return configuratorIRI;
    }

    public IRI getModelIRI() {
        return modelIRI;
    }

    public String getConfiguratorBundleID() {
        return configuratorBundleID;
    }

    public String getConfiguratorConfPid() {
        return configuratorConfPid;
    }

    public ServiceType getConfiguratorServiceType() {
        return configuratorServiceType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThingServiceConfiguratorConfig that = (ThingServiceConfiguratorConfig) o;
        return Objects.equals(modelIRI, that.modelIRI) && Objects.equals(
                configuratorIRI,
                that.configuratorIRI
        ) && Objects.equals(configuratorConfPid, that.configuratorConfPid) && Objects.equals(
                configuratorBundleID,
                that.configuratorBundleID
        ) && configuratorServiceType == that.configuratorServiceType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                modelIRI,
                configuratorIRI,
                configuratorConfPid,
                configuratorBundleID,
                configuratorServiceType
        );
    }

    public static class Builder {
        private IRI modelIRI;
        private IRI configuratorIRI;
        private String configuratorPID;
        private String configuratorBundleID;
        private ServiceType configuratorServiceType;

        public Builder processStatement(Statement statement) {
            var pred = statement.getPredicate();
            if (pred.equals(MODEL_IRI)) {
                modelIRI = (IRI) statement.getObject();
            } else if (pred.equals(CONFIGURATOR_SERVICE_TYPE) && statement.getObject().equals(FACTORY)) {
                configuratorServiceType = ServiceType.FACTORY;
            } else if (pred.equals(CONFIGURATOR_SERVICE_TYPE) && statement.getObject().equals(SINGLETON)) {
                configuratorServiceType = ServiceType.SINGLETON;
            } else if (pred.equals(CONFIGURATOR_BUNDLE_ID)) {
                configuratorBundleID = statement.getObject().stringValue();
            } else if (pred.equals(CONFIGURATOR_CONF_PID)) {
                configuratorPID = statement.getObject().stringValue();
            } else if (pred.equals(RDF.TYPE) && statement.getObject().equals(THING_SERVICE_CONFIGURATOR)) {
                configuratorIRI = (IRI) statement.getSubject();
            }
            return this;
        }

        public Builder setModel(Model model) {
            model.forEach(this::processStatement);
            return this;
        }

        public ThingServiceConfiguratorConfig build() {
            return new ThingServiceConfiguratorConfig(
                    configuratorIRI,
                    modelIRI,
                    configuratorPID,
                    configuratorBundleID,
                    configuratorServiceType
            );
        }
    }
}
