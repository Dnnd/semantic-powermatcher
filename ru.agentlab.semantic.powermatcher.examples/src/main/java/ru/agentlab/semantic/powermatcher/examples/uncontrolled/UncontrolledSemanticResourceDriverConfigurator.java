package ru.agentlab.semantic.powermatcher.examples.uncontrolled;

import org.eclipse.rdf4j.model.IRI;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
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

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
@Designate(ocd = UncontrolledSemanticResourceDriverConfigurator.Config.class, factory = true)
public class UncontrolledSemanticResourceDriverConfigurator implements ThingServiceConfigurator {
    public static final String THING_IRI_PROPERTY = "thingIri";
    public static final String DRIVER_CONFIG_NAME =
            "ru.agentlab.semantic.powermatcher.UncontrolledSemanticResourceDriver";

    private volatile Config config;

    @ObjectClassDefinition
    public @interface Config {
        @AttributeDefinition(name = ThingServiceConfigurator.MODEL_IRI_PROPERTY)
        String modelIRI();
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
        props.put(THING_IRI_PROPERTY, thing.getIRI().stringValue());
        return props;
    }

    @Override
    public IRI getModelIRI() {
        return iri(config.modelIRI());
    }

    @Override
    public String getConfigurationPID() {
        return DRIVER_CONFIG_NAME;
    }

    @Override
    public String getBundleID() {
        return "ru.agentlab.semantic.powermatcher.examples";
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.FACTORY;
    }
}
