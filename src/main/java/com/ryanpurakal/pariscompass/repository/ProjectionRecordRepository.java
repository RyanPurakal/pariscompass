package com.ryanpurakal.pariscompass.repository;

import com.ryanpurakal.pariscompass.domain.ProjectionRecord;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProjectionRecordRepository extends JpaRepository<ProjectionRecord, Long> {

    /**
     * Newest reusable projection for exactly these inputs and this model.
     * Reusable means a model result (VALID or REPAIRED), or a fallback produced with no model configured:
     * both are what the same request would produce again. A fallback caused by a model failure is not
     * reused, so the next request tries the model again.
     */
    @Query("""
            SELECT p FROM ProjectionRecord p
            WHERE p.iso3 = :iso3 AND p.inputHash = :hash
              AND ((:model IS NULL AND p.model IS NULL) OR p.model = :model)
              AND (p.status <> 'FALLBACK' OR p.model IS NULL)
              AND p.createdAt > :since
            ORDER BY p.createdAt DESC
            """)
    List<ProjectionRecord> findReusable(@Param("iso3") String iso3, @Param("hash") String hash,
                                        @Param("model") String model, @Param("since") Instant since, Limit limit);

    List<ProjectionRecord> findByIso3OrderByCreatedAtDesc(String iso3, Limit limit);
}
