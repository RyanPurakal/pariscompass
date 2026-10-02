package com.ryanpurakal.pariscompass.config;

import com.ryanpurakal.pariscompass.projection.ProjectionModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** The model bean exists only when a key is configured; without one, projections use the fallback. */
class GeminiConfigTest {

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    @Import(GeminiConfig.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("app.gemini.model=gemini-2.5-flash", "app.cors.allowed-origins=http://localhost:5173");

    @Test
    void createsModelWhenKeyIsSet() {
        runner.withPropertyValues("app.gemini.api-key=test-key").run(ctx -> {
            assertThat(ctx).hasSingleBean(ProjectionModel.class);
            assertThat(ctx.getBean(ProjectionModel.class).name()).isEqualTo("gemini-2.5-flash");
        });
    }

    @Test
    void noModelWithoutKey() {
        runner.withPropertyValues("app.gemini.api-key=").run(ctx -> assertThat(ctx).doesNotHaveBean(ProjectionModel.class));
    }

    @Test
    void blankKeyCountsAsMissing() {
        runner.withPropertyValues("app.gemini.api-key=   ").run(ctx -> assertThat(ctx).doesNotHaveBean(ProjectionModel.class));
    }
}
