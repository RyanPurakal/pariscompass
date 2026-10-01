package com.ryanpurakal.pariscompass.model;

import java.util.List;

/**
 * Countries ranked by one metric in one year. {@code countriesWithData} is how many countries
 * have a value that year (the ranking's denominator), which may exceed {@code entries.size()}.
 * Ties share a rank (SQL RANK()).
 */
public record RankingResponse(String metric, String unit, int year, String order, int countriesWithData,
                              List<Entry> entries) {

    public record Entry(int rank, String iso3, String name, double value) {
    }
}
