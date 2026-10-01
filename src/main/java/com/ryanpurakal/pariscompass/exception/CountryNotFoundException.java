package com.ryanpurakal.pariscompass.exception;

/** Thrown when an ISO3 code does not match any country in the dataset. Mapped to 404. */
public class CountryNotFoundException extends RuntimeException {
    private final String iso3;

    public CountryNotFoundException(String iso3) {
        super("No country found with ISO3 code '" + iso3 + "'");
        this.iso3 = iso3;
    }

    public String getIso3() {
        return iso3;
    }
}
