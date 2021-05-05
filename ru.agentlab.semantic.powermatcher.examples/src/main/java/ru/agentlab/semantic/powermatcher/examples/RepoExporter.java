package ru.agentlab.semantic.powermatcher.examples;

import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;

@Component(
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true
)
@Designate(ocd = RepoExporter.Config.class, factory = true)
public class RepoExporter {

    private SailRepository repository;
    private static final Logger logger = LoggerFactory.getLogger(RepoExporter.class);
    private Disposable subscription;

    enum ExporterBackends {
        FILE,
        REPOSITORY
    }

    @ObjectClassDefinition
    public @interface Config {
        long interval() default 60;

        String unit() default "SECONDS";

        String contextToExport() default "";

        @AttributeDefinition(
                options = {
                        @Option(label = "File", value = "FILE"),
                        @Option(label = "Repository", value = "REPOSITORY")
                }
        )
        String export_backend() default "FILE";

        String export_repository() default "";

        String export_targetUri() default "";
    }

    @Reference
    public void bindSourceRepository(SailRepositoryProvider provider) {
        this.repository = provider.getRepository();
    }

    @Activate
    public void activate(Config config) {
        var executor = Executors.newSingleThreadScheduledExecutor();
        var scheduler = Schedulers.fromExecutor(executor);
        var interval = Duration.of(
                config.interval(),
                ChronoUnit.valueOf(config.unit())
        );
        var backendType = ExporterBackends.valueOf(config.export_backend());
        ExporterBackend backend = switch (backendType) {
            case FILE -> new FileExportBackend(new File(config.export_targetUri()));
            case REPOSITORY -> new RepositoryExportBackend(
                    RemoteRepositoryManager.getInstance(config.export_targetUri())
                                           .getRepository(config.export_repository())
            );
        };
        subscription = Flux.interval(interval, scheduler)
                           .subscribe(tick -> exportObservations(backend, config.contextToExport()));
    }


    @Deactivate
    public void deactivate() {
        subscription.dispose();
    }

    public void exportObservations(ExporterBackend backend, String context) {
        if (context.equals("")) {
            exportObservations(backend);
        }
        exportObservations(backend, iri(context));
    }

    public void exportObservations(ExporterBackend backend, Resource... context) {
        try (var conn = repository.getConnection()) {
            backend.exportData(conn, context);
        } catch (Exception exception) {
            logger.error("unable to export data", exception);
        }
    }

}
