package com.ryanpurakal.pariscompass.etl;

import com.ryanpurakal.pariscompass.config.AppProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

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

    public EtlRunner(EtlService etlService, AppProperties properties, ConfigurableApplicationContext context) {
        this.etlService = etlService;
        this.config = properties.etl();
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!config.runOnStartup()) {
            return;
        }
        EtlRunSummary summary = etlService.runAll();
        if (config.exitAfterRun()) {
            int code = summary.succeeded() ? 0 : 1;
            System.exit(SpringApplication.exit(context, () -> code));
        }
    }
}
