package com.ryanpurakal.pariscompass.etl;

import com.ryanpurakal.pariscompass.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The job's contract with a scheduler: run only when asked, exit 0 on success and 1 on any failed source. */
class EtlRunnerTest {
    private final EtlService service = mock(EtlService.class);
    private final List<Integer> exitCodes = new ArrayList<>();

    private EtlRunner runner(boolean runOnStartup, boolean exitAfterRun) {
        AppProperties props = new AppProperties(new AppProperties.Cors(List.of("http://x")),
                new AppProperties.Gemini("m", "", false),
                new AppProperties.Etl(runOnStartup, exitAfterRun, false, Map.of()), null, null);
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();
        return new EtlRunner(service, props, context, exitCodes::add);
    }

    private static EtlRunSummary summary(boolean succeeded) {
        return new EtlRunSummary(1, succeeded, 10, List.of());
    }

    @Test
    void doesNothingUnlessRunOnStartupIsSet() {
        runner(false, true).run(new DefaultApplicationArguments());
        verify(service, never()).runAll();
        assertThat(exitCodes).isEmpty();
    }

    @Test
    void runsWithoutExitingWhenExitAfterRunIsOff() {
        when(service.runAll()).thenReturn(summary(true));
        runner(true, false).run(new DefaultApplicationArguments());
        verify(service).runAll();
        assertThat(exitCodes).isEmpty();
    }

    @Test
    void exitsZeroOnSuccess() {
        when(service.runAll()).thenReturn(summary(true));
        runner(true, true).run(new DefaultApplicationArguments());
        assertThat(exitCodes).containsExactly(0);
    }

    @Test
    void exitsOneWhenAnySourceFailed() {
        when(service.runAll()).thenReturn(summary(false));
        runner(true, true).run(new DefaultApplicationArguments());
        assertThat(exitCodes).containsExactly(1);
    }
}
