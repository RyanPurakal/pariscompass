package com.ryanpurakal.pariscompass.etl;

import com.ryanpurakal.pariscompass.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.function.IntConsumer;

/**
 * Runs the ETL at startup when app.etl.run-on-startup is set (the "etl" profile does this).
 * With exit-after-run the process exits 0 on success and 1 on failure, so it works as a
 * scheduled job or CI step using the same artifact as the API.
 */
@Component
public class EtlRunner implements ApplicationRunner {
    private final EtlService etlService;
    private final AppProperties.Etl config;
    private final ConfigurableApplicationContext context;
    private final IntConsumer exit;

    @Autowired
    public EtlRunner(EtlService etlService, AppProperties properties, ConfigurableApplicationContext context) {
        this(etlService, properties, context, System::exit);
    }

    /** {@code exit} is System::exit in production; tests pass a recorder instead of ending the JVM. */
    EtlRunner(EtlService etlService, AppProperties properties, ConfigurableApplicationContext context, IntConsumer exit) {
        this.etlService = etlService;
        this.config = properties.etl();
        this.context = context;
        this.exit = exit;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!config.runOnStartup()) {
            return;
        }
        EtlRunSummary summary = etlService.runAll();
        if (config.exitAfterRun()) {
            int code = summary.succeeded() ? 0 : 1;
            exit.accept(SpringApplication.exit(context, () -> code));
        }
    }
}
