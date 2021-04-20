package ru.agentlab.semantic.wot.services.providers;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.osgi.service.component.annotations.Reference;
import ru.agentlab.changetracking.filter.ChangetrackingFilter;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;
import ru.agentlab.semantic.wot.services.repositories.ThingServiceImplementationRepository;
import ru.agentlab.semantic.wot.thing.ConnectionContext;

import java.util.Map;
import java.util.concurrent.Executors;

public class ThingServiceConfiguratorFactory {
    private SailRepository repository;

    @Reference
    public void setSailRepository(SailRepositoryProvider provider) {
        this.repository = provider.getRepository();
    }

    public void activate() {
        var executor = Executors.newSingleThreadExecutor();
        var connCtx = new ConnectionContext(executor, repository.getConnection());

        var sailConn = (ChangeTrackerConnection) connCtx.getSailConnection();
        var thingServicesRepo = new ThingServiceImplementationRepository(connCtx);
        thingServicesRepo.

        var filter = ChangetrackingFilter.builder()
                .addPattern()

        sailConn.events(connCtx.getScheduler())
                .flatMap(changes -> {
                });

    }
}
