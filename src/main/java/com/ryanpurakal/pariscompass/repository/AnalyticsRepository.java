package com.ryanpurakal.pariscompass.repository;

import com.ryanpurakal.pariscompass.domain.Observation;
import com.ryanpurakal.pariscompass.domain.ObservationId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Read queries over the observation table. Each states which index serves it:
 * the primary key (iso3, metric_code, year) or idx_observation_metric_year (metric_code, year) INCLUDE (iso3, value).
 */
public interface AnalyticsRepository extends Repository<Observation, ObservationId> {

    /** Primary key range scan per requested metric. */
    @Query(nativeQuery = true, value = """
            SELECT o.metric_code AS metricCode, o.year AS year, o.value AS value
            FROM observation o
            WHERE o.iso3 = :iso3 AND o.metric_code IN (:metrics) AND o.year BETWEEN :from AND :to
            ORDER BY o.metric_code, o.year
            """)
    List<MetricPoint> findSeries(@Param("iso3") String iso3, @Param("metrics") Collection<String> metrics,
                                 @Param("from") int from, @Param("to") int to);

    /** Primary key range scan per requested country. */
    @Query(nativeQuery = true, value = """
            SELECT o.iso3 AS iso3, o.year AS year, o.value AS value
            FROM observation o
            WHERE o.iso3 IN (:countries) AND o.metric_code = :metric AND o.year BETWEEN :from AND :to
            ORDER BY o.iso3, o.year
            """)
    List<CountryPoint> findComparison(@Param("countries") Collection<String> countries, @Param("metric") String metric,
                                      @Param("from") int from, @Param("to") int to);

    /** Covering index on (metric_code, year). */
    @Query(nativeQuery = true, value = """
            SELECT CAST(rank() OVER (ORDER BY o.value DESC) AS int) AS rank, o.iso3 AS iso3, c.name AS name,
                   o.value AS value, CAST(count(*) OVER () AS int) AS total
            FROM observation o JOIN country c ON c.iso3 = o.iso3
            WHERE o.metric_code = :metric AND o.year = :year
            ORDER BY o.value DESC, c.name
            LIMIT :limit
            """)
    List<RankRow> findRankingDesc(@Param("metric") String metric, @Param("year") int year, @Param("limit") int limit);

    /** Covering index on (metric_code, year). */
    @Query(nativeQuery = true, value = """
            SELECT CAST(rank() OVER (ORDER BY o.value ASC) AS int) AS rank, o.iso3 AS iso3, c.name AS name,
                   o.value AS value, CAST(count(*) OVER () AS int) AS total
            FROM observation o JOIN country c ON c.iso3 = o.iso3
            WHERE o.metric_code = :metric AND o.year = :year
            ORDER BY o.value ASC, c.name
            LIMIT :limit
            """)
    List<RankRow> findRankingAsc(@Param("metric") String metric, @Param("year") int year, @Param("limit") int limit);

    /** Latest year with at least 90% of the metric's best per-year country coverage. */
    @Query(nativeQuery = true, value = """
            WITH per_year AS (
                SELECT year, count(*) AS n FROM observation WHERE metric_code = :metric GROUP BY year
            )
            SELECT CAST(max(year) AS int) FROM per_year WHERE n >= 0.9 * (SELECT max(n) FROM per_year)
            """)
    Integer findDefaultYear(@Param("metric") String metric);

    @Query(nativeQuery = true, value = """
            SELECT m.code AS code, m.name AS name, m.unit AS unit, m.description AS description,
                   d.code AS sourceCode, d.name AS sourceName, d.homepage AS sourceHomepage, d.citation AS sourceCitation,
                   count(o.value) AS valueCount, CAST(count(DISTINCT o.iso3) AS int) AS countries,
                   CAST(min(o.year) AS int) AS firstYear, CAST(max(o.year) AS int) AS lastYear
            FROM metric m
            JOIN data_source d ON d.code = m.source_code
            LEFT JOIN observation o ON o.metric_code = m.code
            GROUP BY m.code, d.code
            ORDER BY m.code
            """)
    List<MetricCatalogRow> findMetricCatalog();

    interface MetricPoint {
        String getMetricCode();
        int getYear();
        double getValue();
    }

    interface CountryPoint {
        String getIso3();
        int getYear();
        double getValue();
    }

    interface RankRow {
        int getRank();
        String getIso3();
        String getName();
        double getValue();
        int getTotal();
    }

    interface MetricCatalogRow {
        String getCode();
        String getName();
        String getUnit();
        String getDescription();
        String getSourceCode();
        String getSourceName();
        String getSourceHomepage();
        String getSourceCitation();
        long getValueCount();
        int getCountries();
        Integer getFirstYear();
        Integer getLastYear();
    }
}
