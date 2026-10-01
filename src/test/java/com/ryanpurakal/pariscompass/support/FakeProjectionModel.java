package com.ryanpurakal.pariscompass.support;

import com.ryanpurakal.pariscompass.projection.ProjectionModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Scripted stand-in for Gemini: returns queued replies (or throws) in order and records every prompt. */
public class FakeProjectionModel implements ProjectionModel {
    private final Deque<Supplier<String>> script = new ArrayDeque<>();
    private final List<String> prompts = new ArrayList<>();

    public FakeProjectionModel reply(String text) {
        script.add(() -> text);
        return this;
    }

    /** Replies after a delay, to hold a generation in flight while other requests arrive. */
    public FakeProjectionModel replyAfter(String text, long millis) {
        script.add(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return text;
        });
        return this;
    }

    public FakeProjectionModel fail(RuntimeException e) {
        script.add(() -> {
            throw e;
        });
        return this;
    }

    @Override
    public Reply generate(String prompt, Map<String, Object> responseJsonSchema) {
        Supplier<String> next;
        synchronized (this) {
            prompts.add(prompt);
            next = script.poll();
        }
        if (next == null) {
            throw new IllegalStateException("FakeProjectionModel: no scripted reply left");
        }
        return new Reply(next.get(), 100, 50);
    }

    @Override
    public String name() {
        return "fake-model";
    }

    public synchronized List<String> prompts() {
        return List.copyOf(prompts);
    }

    public synchronized void reset() {
        script.clear();
        prompts.clear();
    }
}
