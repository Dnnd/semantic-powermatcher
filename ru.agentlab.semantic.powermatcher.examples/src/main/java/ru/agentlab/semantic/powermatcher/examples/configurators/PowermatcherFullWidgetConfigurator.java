package ru.agentlab.semantic.powermatcher.examples.configurators;

import org.eclipse.rdf4j.model.IRI;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
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

@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = PowermatcherFullWidgetConfigurator.Config.class, factory = true)
public class PowermatcherFullWidgetConfigurator implements ThingServiceConfigurator {

    @ObjectClassDefinition
    public @interface Config {
        @AttributeDefinition(name = ThingServiceConfigurator.MODEL_IRI_PROPERTY)
        String modelIRI();
    }

    private Config config;

    @Activate
    public void activate(Config config) {
        this.config = config;
    }

    @Override
    public Dictionary<String, ?> getConfiguration(Thing thing, ConnectionContext context) {
        return new Hashtable<>();
    }

    @Override
    public IRI getModelIRI() {
        return iri(config.modelIRI());
    }

    @Override
    public String getConfigurationPID() {
        return "net.powermatcher.fpai.widget.FullWidget";
    }

    @Override
    public String getBundleID() {
        return "net.powermatcher.fpai.controller";
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.SINGLETON;
    }
}
