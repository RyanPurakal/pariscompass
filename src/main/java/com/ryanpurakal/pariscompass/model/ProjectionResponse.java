package com.ryanpurakal.pariscompass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionResponse {
    private String country;
    private String projection;
    private String model;
    private Instant generatedAt;
}

