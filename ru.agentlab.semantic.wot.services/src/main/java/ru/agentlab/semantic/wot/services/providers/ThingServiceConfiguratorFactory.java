package ru.agentlab.semantic.wot.services.providers;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import reactor.core.Disposable;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;
import ru.agentlab.semantic.wot.services.repositories.ThingServiceImplementationRepository;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.utils.Utils;

import java.io.IOException;
import java.util.concurrent.Executors;

import static ru.agentlab.semantic.wot.services.api.ThingServiceConfigurator.MODEL_IRI_PROPERTY;

@Component(scope = ServiceScope.SINGLETON, immediate = true)
public class ThingServiceConfiguratorFactory {
    private SailRepository repository;
    private ConfigurationAdmin configurationAdmin;
    private Disposable subscription;

    @Reference
    public void setSailRepository(SailRepositoryProvider provider) {
        this.repository = provider.getRepository();
    }

    @Reference
    public void bindConfigurationAdmin(ConfigurationAdmin admin) {
        this.configurationAdmin = admin;
    }

    public void activate() {
        var executor = Executors.newSingleThreadExecutor();
        var connCtx = new ConnectionContext(executor, repository.getConnection());

        var thingServicesRepo = new ThingServiceImplementationRepository(connCtx);
        subscription = thingServicesRepo.discoverThingServiceImplementations()
                                        .subscribe(implementation -> Utils.supplyAsyncWithCancel(() -> {
                                            try {
                                                var conf = configurationAdmin.getConfiguration(
                                                        implementation.getConfigurationID(),
                                                        implementation.getBundleID()
                                                );
                                                var props = conf.getProperties();
                                                props.put(
                                                        MODEL_IRI_PROPERTY,
                                                        implementation.getModelIRI().stringValue()
                                                );
                                                conf.update();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }, connCtx.getExecutor()));
    }

    public void deactivate() {
        subscription.dispose();
    }
}
