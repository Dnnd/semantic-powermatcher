package ru.agentlab.semantic.powermatcher.examples.heater;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.config.SailRegistry;
import org.junit.jupiter.api.Test;
import ru.agentlab.changetracking.sail.ChangeTrackingFactory;
import ru.agentlab.changetracking.utils.EmbeddedChangetrackingRepo;
import ru.agentlab.semantic.powermatcher.examples.RepoExporter;

import javax.measure.Measure;
import javax.measure.unit.SI;
import java.io.IOException;
import java.lang.annotation.Annotation;


public class HeaterDriverTest {
    @Test
    void test() throws IOException, InterruptedException {
        SailRegistry.getInstance().add(new ChangeTrackingFactory());
        try (EmbeddedChangetrackingRepo repo = EmbeddedChangetrackingRepo.makeTempRepository("test");
             var conn = repo.getConnection()
        ) {
            RepoExporter exporter = new RepoExporter();
            SailRepository repository = (SailRepository) conn.getRepository();
            var hd = new HeaterDriver();
            var hsm = new HeaterProvider();
            hsm.bindSailRepository(() -> repository);
            hd.bindSailRepositoryProvider(() -> repository);
            exporter.bindSourceRepository(() -> repository);

            hsm.activate(new HeaterSimulationConfig() {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return getClass();
                }

                @Override
                public String thingIRI() {
                    return "https://example.agentlab.ru/#Heater_1";
                }

                @Override
                public String stateContext() {
                    return "https://observations.agentlab.ru/";
                }

                @Override
                public int updateFrequency() {
                    return 1000;
                }

                @Override
                public String thingContext() {
                    return "https://things.agentlab.ru";
                }
            });
            hd.activate(new HeaterDriver.Config() {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return getClass();
                }

                @Override
                public String thingIRI() {
                    return "https://example.agentlab.ru/#Heater_1";
                }

                @Override
                public String stateContext() {
                    return "https://observations.agentlab.ru";
                }

            });

            exporter.activate(new RepoExporter.Config() {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return getClass();
                }

                @Override
                public long interval() {
                    return 3;
                }

                @Override
                public String unit() {
                    return "SECONDS";
                }

                @Override
                public String contextToExport() {
                    return "https://things.agentlab.ru";
                }

                @Override
                public String export_backend() {
                    return "FILE";
                }

                @Override
                public String export_repository() {
                    return "";
                }

                @Override
                public String export_targetUri() {
                    return "/var/tmp/correct_things.ttl";
                }
            });
            for (int i = 0; i < 5; ++i) {
                hd.handleControlParameters(() -> Measure.valueOf(1000, SI.WATT));
                Thread.sleep(1000);
            }
            hd.deactivate();
            hsm.deactivate();
            exporter.deactivate();
        }
    }
}
