package ru.agentlab.semantic.powermatcher.examples;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;

import java.io.IOException;

@Component(immediate = true, scope = ServiceScope.SINGLETON, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = ExampleConfiguratorsLoader.Config.class)
public class ExampleConfiguratorsLoader {

    @ObjectClassDefinition
    public @interface Config {
        boolean deleteOnDeactivate() default false;
    }

    private SailRepository repository;

    @Reference
    public void bindSailSailRepository(SailRepositoryProvider provider) {
        repository = provider.getRepository();
    }

    @Activate
    public void activate() throws IOException {
        try (var configurators = Utils.openResourceStream("configurators.ttl")) {
            try (var conn = repository.getConnection()) {
                var configuratorsModel = Rio.parse(configurators, RDFFormat.TURTLE);
                conn.add(configuratorsModel);
            }
        }
    }
}
