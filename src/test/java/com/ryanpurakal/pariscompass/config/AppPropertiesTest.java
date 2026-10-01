package com.ryanpurakal.pariscompass.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Startup validation rules for app.* settings, checked without booting the web server. */
class AppPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("app.gemini.model=gemini-2.5-flash", "app.cors.allowed-origins=http://localhost:5173");

    @Test
    void missingKeyIsAllowedWhenNotRequired() {
        runner.withPropertyValues("app.gemini.api-key=", "app.gemini.required=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(AppProperties.class).gemini().isConfigured()).isFalse();
                });
    }

    @Test
    void missingKeyFailsStartupWhenRequired() {
        runner.withPropertyValues("app.gemini.api-key=", "app.gemini.required=true")
                .run(ctx -> assertThat(ctx).getFailure()
                        .rootCause().hasMessageContaining("GEMINI_API_KEY must be set"));
    }

    @Test
    void emptyCorsOriginsFailStartup() {
        runner.withPropertyValues("app.cors.allowed-origins=")
                .run(ctx -> assertThat(ctx).getFailure()
                        .rootCause().hasMessageContaining("CORS_ALLOWED_ORIGINS"));
    }

    @Test
    void wildcardCorsOriginFailsStartup() {
        runner.withPropertyValues("app.cors.allowed-origins=*")
                .run(ctx -> assertThat(ctx).getFailure()
                        .rootCause().hasMessageContaining("not '*'"));
    }

    @Test
    void commaSeparatedOriginsBindToList() {
        runner.withPropertyValues("app.cors.allowed-origins=https://a.example,https://b.example")
                .run(ctx -> assertThat(ctx.getBean(AppProperties.class).cors().allowedOrigins())
                        .containsExactly("https://a.example", "https://b.example"));
    }

    @Test
    void toStringNeverExposesApiKey() {
        AppProperties.Gemini gemini = new AppProperties.Gemini("m", "AIzaSecretValue", true);
        assertThat(gemini.toString()).doesNotContain("AIzaSecretValue").contains("****");
    }
}
