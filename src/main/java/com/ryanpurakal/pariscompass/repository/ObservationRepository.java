package com.ryanpurakal.pariscompass.repository;

import com.ryanpurakal.pariscompass.domain.Observation;
import com.ryanpurakal.pariscompass.domain.ObservationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ObservationRepository extends JpaRepository<Observation, ObservationId> {

    /**
     * Most recent value of every metric for one country. Postgres DISTINCT ON keeps the first row
     * per metric_code in ORDER BY order, i.e. the latest year. Reads only the primary-key range for iso3.
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (o.metric_code) o.metric_code AS metricCode, o.year AS year, o.value AS value
            FROM observation o
            WHERE o.iso3 = :iso3
            ORDER BY o.metric_code, o.year DESC
            """)
    List<LatestValue> findLatestByCountry(@Param("iso3") String iso3);

    interface LatestValue {
        String getMetricCode();

        int getYear();

        double getValue();
    }
}
