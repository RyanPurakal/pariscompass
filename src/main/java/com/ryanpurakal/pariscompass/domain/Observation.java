package com.ryanpurakal.pariscompass.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One value of one metric for one country in one year. Read-only (@Immutable):
 * the ETL owns writes, so Hibernate never tracks these for dirty checking.
 */
@Entity
@Immutable
@IdClass(ObservationId.class)
@Table(name = "observation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Observation {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "iso3", length = 3)
    private String iso3;

    @Id
    @Column(name = "metric_code", length = 64)
    private String metricCode;

    @Id
    @Column(name = "year")
    private Short year;

    @Column(name = "value", nullable = false)
    private Double value;
}
