package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.Literals;
import org.eclipse.rdf4j.model.vocabulary.XSD;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static ru.agentlab.semantic.wot.services.api.WotServicesVocabulary.*;

public class DeclarativeServiceLaunchConfiguration {
    final private String configurationPID;
    final private String bundleID;
    final private ServiceType serviceType;
    final private Dictionary<String, ?> properties;

    public DeclarativeServiceLaunchConfiguration(String configurationPID,
                                                 String bundleID,
                                                 ServiceType serviceType,
                                                 Dictionary<String, ?> properties) {
        this.configurationPID = configurationPID;
        this.bundleID = bundleID;
        this.serviceType = serviceType;
        this.properties = properties;
    }

    public String getServiceConfigurationPID() {
        return configurationPID;
    }

    public String getBundleID() {
        return bundleID;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public Dictionary<String, ?> getProperties() {
        return properties;
    }

    public static class Builder {
        private String configurationPID;
        private String bundleID;
        private ServiceType serviceType;
        private Map<String, Object> properties = new HashMap<>();

        private Object unwrapLiteral(Literal literal) {
            var datatype = XSD.Datatype.from(literal.getDatatype()).orElseThrow();
            return switch (datatype) {
                case STRING -> literal.stringValue();
                case BOOLEAN -> Literals.getBooleanValue(literal, false);
                case FLOAT, DOUBLE -> Literals.getDoubleValue(literal, -1);
                case LONG -> Literals.getLongValue(literal, -1);
                case INT -> Literals.getIntValue(literal, -1);
                default -> throw new RuntimeException("unable to parse literal " + literal.stringValue());
            };
        }

        public Builder setFromStatements(Iterable<Statement> statements) {
            for (Statement statement : statements) {
                if (statement.getPredicate().equals(CONFIGURES_SERVICE)) {
                    setConfigurationPID(statement.getObject().stringValue());
                } else if (statement.getPredicate().equals(CONFIGURES_SERVICE_IN_BUNDLE)) {
                    setBundleID(statement.getObject().stringValue());
                } else if (statement.getPredicate().equals(CONFIGURATION_PROPERTY_VALUE)) {
                    addProperty(
                            statement.getSubject().stringValue(),
                            unwrapLiteral((Literal) statement.getObject())
                    );
                }
            }
            return this;
        }

        public Builder setConfigurationPID(String configurationPID) {
            this.configurationPID = configurationPID;
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

        public Builder setProperties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        public Builder addProperty(String propertyName, Object propertyValue) {
            properties.put(propertyName, propertyValue);
            return this;
        }

        public DeclarativeServiceLaunchConfiguration build() {
            return new DeclarativeServiceLaunchConfiguration(
                    configurationPID,
                    bundleID,
                    serviceType,
                    new Hashtable<>(properties)
            );
        }
    }

}
