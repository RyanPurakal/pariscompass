package com.ryanpurakal.pariscompass.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads all CSV datasets from src/main/resources/data/ into memory at startup.
 * After @PostConstruct completes, the four maps are immutable in practice — no other
 * code writes to them. Downstream services treat them as read-only.
 */
@Slf4j
@Component
@Getter
public class DataLoader {
    private final Map<String, Map<String, Co2Data>> co2Data = new HashMap<>();
    private final Map<String, Map<String, RenewablesData>> renewablesData = new HashMap<>();
    private final Map<String, Map<String, TemperatureData>> temperatureData = new HashMap<>();
    private final Map<String, String> countryNames = new HashMap<>();

    @PostConstruct
    public void loadData() {
        try {
            loadCo2Data();
            loadRenewablesData();
            loadTemperatureData();
            loadCountryNames();
            log.info("Data loaded successfully. Countries: {}", countryNames.size());
        } catch (Exception e) {
            log.error("Error loading data", e);
        }
    }

    private void loadCo2Data() throws IOException, CsvException {
        ClassPathResource resource = new ClassPathResource("data/co2_data.csv");
        try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            // Skip header
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length >= 5) {
                    String isoCode = row[1].trim();
                    String year = row[2].trim();
                    try {
                        double co2 = Double.parseDouble(row[3]);
                        double co2PerCapita = Double.parseDouble(row[4]);
                        co2Data.computeIfAbsent(isoCode, k -> new HashMap<>())
                                .put(year, new Co2Data(co2, co2PerCapita));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid number format in CO2 data: {}", String.join(",", row));
                    }
                }
            }
        }
    }

    private void loadRenewablesData() throws IOException, CsvException {
        ClassPathResource resource = new ClassPathResource("data/renewables_data.csv");
        try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            // Skip header
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length >= 3) {
                    String iso3 = row[0].trim();
                    String year = row[1].trim();
                    try {
                        double renewablesShare = Double.parseDouble(row[2]);
                        renewablesData.computeIfAbsent(iso3, k -> new HashMap<>())
                                .put(year, new RenewablesData(renewablesShare));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid number format in renewables data: {}", String.join(",", row));
                    }
                }
            }
        }
    }

    private void loadTemperatureData() throws IOException, CsvException {
        ClassPathResource resource = new ClassPathResource("data/temperature_data.csv");
        try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            // Skip header
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length >= 3) {
                    String iso3 = row[0].trim();
                    String year = row[1].trim();
                    try {
                        double tempAnomaly = Double.parseDouble(row[2]);
                        temperatureData.computeIfAbsent(iso3, k -> new HashMap<>())
                                .put(year, new TemperatureData(tempAnomaly));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid number format in temperature data: {}", String.join(",", row));
                    }
                }
            }
        }
    }

    private void loadCountryNames() throws IOException, CsvException {
        ClassPathResource resource = new ClassPathResource("data/countries.csv");
        try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            // Skip header
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length >= 2) {
                    countryNames.put(row[0].trim(), row[1].trim());
                }
            }
        }
    }

    public record Co2Data(double co2TotalMt, double co2PerCapita) {}
    public record RenewablesData(double renewablesShare) {}
    public record TemperatureData(double tempAnomaly) {}
}

