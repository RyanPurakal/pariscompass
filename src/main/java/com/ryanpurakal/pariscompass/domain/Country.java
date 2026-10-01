package com.ryanpurakal.pariscompass.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Read-only mapping of the country table. Rows are written by the ETL, never through JPA. */
@Entity
@Table(name = "country")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Country {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "iso3", length = 3)
    private String iso3;

    @Column(name = "name", nullable = false)
    private String name;
}
