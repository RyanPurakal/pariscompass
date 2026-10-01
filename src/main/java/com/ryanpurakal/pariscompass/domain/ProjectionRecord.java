package com.ryanpurakal.pariscompass.domain;

import com.ryanpurakal.pariscompass.projection.ProjectionOutput;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/** A stored projection. JSON columns are mapped to Java types by Hibernate 6's built-in JSON support. */
@Entity
@Table(name = "projection")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ProjectionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "iso3", length = 3, nullable = false)
    private String iso3;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "generated_by", nullable = false)
    private String generatedBy;

    @Column(name = "model")
    private String model;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "input_hash", length = 64, nullable = false)
    private String inputHash;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "fallback_reason")
    private String fallbackReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_errors", nullable = false)
    private List<String> validationErrors;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "base_year", nullable = false)
    private short baseYear;

    @Column(name = "base_year_co2_mt", nullable = false)
    private double baseYearCo2Mt;

    @Column(name = "alignment_score")
    private Double alignmentScore;

    @Column(name = "alignment_band")
    private String alignmentBand;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output", nullable = false)
    private ProjectionOutput output;
}
