package ru.agentlab.semantic.wot.services.providers;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import ru.agentlab.changetracking.filter.ChangetrackingFilter;
import ru.agentlab.changetracking.filter.Match;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;
import ru.agentlab.semantic.wot.services.api.ThingServiceConfiguratorConfig;
import ru.agentlab.semantic.wot.services.repositories.ThingServiceImplementationRepository;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.utils.Utils;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static ru.agentlab.semantic.wot.services.api.ThingServiceConfigurator.CONFIGURATOR_IRI_PROPERTY;
import static ru.agentlab.semantic.wot.services.api.ThingServiceConfigurator.MODEL_IRI_PROPERTY;

@Component(scope = ServiceScope.SINGLETON, immediate = true)
@Designate(ocd = ThingServiceConfiguratorFactory.Config.class)
public class ThingServiceConfiguratorFactory {
    private SailRepository repository;
    private ConfigurationAdmin configurationAdmin;
    private Disposable subscription;
    private final Map<ThingServiceConfiguratorConfig, Configuration> configurations = new ConcurrentHashMap<>();
    private final static Logger logger = LoggerFactory.getLogger(ThingServiceConfiguratorFactory.class);

    @ObjectClassDefinition
    public @interface Config {

        // TODO: actually use this property
        boolean deactivateConfiguratorsOnDeactivate() default true;
    }

    @Reference
    public void setSailRepository(SailRepositoryProvider provider) {
        this.repository = provider.getRepository();
    }

    @Reference
    public void bindConfigurationAdmin(ConfigurationAdmin admin) {
        this.configurationAdmin = admin;
    }

    @Activate
    public void activate(Config config) {
        logger.info("Activating configurators factory...");
        var executor = Executors.newSingleThreadExecutor();
        var connCtx = new ConnectionContext(executor, repository.getConnection());

        var thingServicesRepo = new ThingServiceImplementationRepository(connCtx);
        subscription = thingServicesRepo.discoverThingServiceImplementations()
                                        .flatMap(implementationMatch -> Utils.supplyAsyncWithCancel(
                                                () -> handleImplementationMatch(implementationMatch),
                                                connCtx.getExecutor()
                                        ))
                                        .doFinally(ignore -> connCtx.close())
                                        .subscribe();
        logger.info("Activating configurators factory... Done");
    }

    public void handleImplementationMatch(Match<ThingServiceConfiguratorConfig> implementationMatch) {
        if (implementationMatch.getFilteredFrom().equals(ChangetrackingFilter.Filtering.ADDED)) {
            activateThingConfigurator(implementationMatch.getData());
        } else {
            deactivateThingConfigurator(implementationMatch.getData());
        }
    }

    private void deactivateThingConfigurator(ThingServiceConfiguratorConfig implementation) {
        configurations.computeIfPresent(implementation, (ignored, configuration) -> {
            try {
                configuration.delete();
            } catch (IOException e) {
                logger.error("unable to deactivate implementation {}", implementation);
            }
            return null;
        });
    }

    private Dictionary<String, Object> getConfigurationProperties(Configuration conf) {
        var props = conf.getProperties();
        if (props == null) {
            return new Hashtable<>();
        }
        return props;
    }

    public void activateThingConfigurator(ThingServiceConfiguratorConfig implementation) {
        logger.info("activating {}...", implementation.getConfiguratorIRI());
        Configuration conf = getConfiguration(implementation);

        var props = getConfigurationProperties(conf);
        props.put(
                MODEL_IRI_PROPERTY,
                implementation.getModelIRI().stringValue()
        );
        props.put(
                CONFIGURATOR_IRI_PROPERTY,
                implementation.getConfiguratorIRI().stringValue()
        );
        configurations.computeIfAbsent(implementation, (ignored) -> {
            try {
                conf.update(props);
                logger.info("activating {}...Done", implementation.getConfiguratorIRI());
            } catch (IOException e) {
                logger.error(
                        "unable to create ThingConfigurator service for {}",
                        implementation
                );
                return null;
            }
            return conf;
        });
    }

    private Configuration getConfiguration(ThingServiceConfiguratorConfig configuratorConfig) {
        try {
            return switch (configuratorConfig.getConfiguratorServiceType()) {
                case SINGLETON -> configurationAdmin.getConfiguration(
                        configuratorConfig.getConfiguratorConfPid(),
                        configuratorConfig.getConfiguratorBundleID()
                );
                case FACTORY -> configurationAdmin.createFactoryConfiguration(
                        configuratorConfig.getConfiguratorConfPid(),
                        configuratorConfig.getConfiguratorBundleID()
                );
            };
        } catch (IOException e) {
            logger.error("unable to create ThingConfigurator configuration for {}", configuratorConfig, e);
            throw new RuntimeException(e);
        }
    }

    @Deactivate
    public void deactivate() {
        subscription.dispose();
        for (Configuration configuration : configurations.values()) {
            try {
                configuration.delete();
            } catch (IOException e) {
                logger.info("Unable to deactivate {}", configuration.getPid());
            }
        }
    }
}
