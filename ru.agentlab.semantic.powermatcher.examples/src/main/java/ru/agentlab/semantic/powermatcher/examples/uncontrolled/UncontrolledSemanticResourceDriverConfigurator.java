package ru.agentlab.semantic.powermatcher.examples.uncontrolled;

import org.eclipse.rdf4j.model.IRI;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import ru.agentlab.semantic.wot.services.api.ServiceType;
import ru.agentlab.semantic.wot.services.api.ThingServiceConfigurator;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;

import java.util.Dictionary;
import java.util.Hashtable;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.wot.services.repositories.WotServicesVocabulary.FACTORY;

@Component(scope = ServiceScope.SINGLETON)
@Designate(ocd = UncontrolledSemanticResourceDriverConfigurator.Config.class)
public class UncontrolledSemanticResourceDriverConfigurator implements ThingServiceConfigurator {
    private volatile Config config;

    @ObjectClassDefinition
    public @interface Config {

        @AttributeDefinition(name = ThingServiceConfigurator.MODEL_IRI_PROPERTY)
        String modelIRI();

        @AttributeDefinition(name = ThingServiceConfigurator.SERVICE_CONFIGURATION_PID)
        String serviceConfigurationPID();

        @AttributeDefinition(name = ThingServiceConfigurator.SERVICE_BUNDLE_ID)
        String serviceBundleID();

        @AttributeDefinition(name = ThingServiceConfigurator.SERVICE_TYPE)
        String serviceType();
    }

    @Activate
    public void activate(Config config) {
        this.config = config;
    }

    @Deactivate
    public void deactivate() {

    }


    @Override
    public Dictionary<String, ?> getConfiguration(Thing thing, ConnectionContext context) {
        Dictionary<String, String> props = new Hashtable<>();
        props.put("thingIRI", thing.getIRI().stringValue());
        return props;
    }

    @Override
    public IRI getModelIRI() {
        return iri(config.modelIRI());
    }

    @Override
    public String getConfigurationPID() {
        return config.serviceConfigurationPID();
    }

    @Override
    public String getBundleID() {
        return config.serviceBundleID();
    }

    @Override
    public ServiceType getServiceType() {
        var serviceType = iri(config.serviceType());
        if (serviceType.equals(FACTORY)) {
            return ServiceType.FACTORY;
        } else {
            return ServiceType.SINGLETON;
        }
    }
}
