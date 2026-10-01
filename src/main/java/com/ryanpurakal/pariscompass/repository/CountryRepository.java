package com.ryanpurakal.pariscompass.repository;

import com.ryanpurakal.pariscompass.domain.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CountryRepository extends JpaRepository<Country, String> {
    List<Country> findAllByOrderByNameAsc();
}
