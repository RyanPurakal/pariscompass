package hackru.AI.service;

import hackru.AI.model.CountryInfo;
import hackru.AI.model.CountryMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CountryMetricsService {
    private final DataLoader dataLoader;

    public CountryMetrics getLatestMetrics(String iso3) {
        if (iso3 == null || iso3.isBlank()) {
            return null;
        }

        String name = dataLoader.getCountryNames().get(iso3);
        if (name == null) {
            log.warn("Country not found: {}", iso3);
            return null;
        }

        CountryMetrics.CountryMetricsBuilder builder = CountryMetrics.builder()
                .iso3(iso3)
                .name(name);

        // Find latest year for CO2 data
        Integer latestYear = findLatestYear(iso3);
        builder.year(latestYear);

        // Get CO2 data
        Map<String, DataLoader.Co2Data> co2DataByYear = dataLoader.getCo2Data().get(iso3);
        if (co2DataByYear != null && latestYear != null) {
            DataLoader.Co2Data co2Data = co2DataByYear.get(String.valueOf(latestYear));
            if (co2Data != null) {
                builder.co2TotalMt(co2Data.co2TotalMt())
                        .co2PerCapita(co2Data.co2PerCapita());
            }
        }

        // Get renewables data
        Map<String, DataLoader.RenewablesData> renewablesDataByYear = dataLoader.getRenewablesData().get(iso3);
        if (renewablesDataByYear != null && latestYear != null) {
            DataLoader.RenewablesData renewablesData = renewablesDataByYear.get(String.valueOf(latestYear));
            if (renewablesData != null) {
                builder.renewablesSharePct(renewablesData.renewablesShare());
            }
        }

        // Get temperature data
        Map<String, DataLoader.TemperatureData> tempDataByYear = dataLoader.getTemperatureData().get(iso3);
        if (tempDataByYear != null && latestYear != null) {
            DataLoader.TemperatureData tempData = tempDataByYear.get(String.valueOf(latestYear));
            if (tempData != null) {
                builder.temperatureAnomalyC(tempData.tempAnomaly());
            }
        }

        // Set source info
        builder.source(CountryMetrics.SourceInfo.builder()
                .co2("Our World in Data")
                .temp("Berkeley Earth")
                .renewables("OWID")
                .build());

        return builder.build();
    }

    private Integer findLatestYear(String iso3) {
        Integer latestYear = null;

        // Check CO2 data
        Map<String, DataLoader.Co2Data> co2DataByYear = dataLoader.getCo2Data().get(iso3);
        if (co2DataByYear != null) {
            for (String yearStr : co2DataByYear.keySet()) {
                try {
                    int year = Integer.parseInt(yearStr);
                    if (latestYear == null || year > latestYear) {
                        latestYear = year;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid year format: {}", yearStr);
                }
            }
        }

        // Check renewables data
        Map<String, DataLoader.RenewablesData> renewablesDataByYear = dataLoader.getRenewablesData().get(iso3);
        if (renewablesDataByYear != null) {
            for (String yearStr : renewablesDataByYear.keySet()) {
                try {
                    int year = Integer.parseInt(yearStr);
                    if (latestYear == null || year > latestYear) {
                        latestYear = year;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid year format: {}", yearStr);
                }
            }
        }

        // Check temperature data
        Map<String, DataLoader.TemperatureData> tempDataByYear = dataLoader.getTemperatureData().get(iso3);
        if (tempDataByYear != null) {
            for (String yearStr : tempDataByYear.keySet()) {
                try {
                    int year = Integer.parseInt(yearStr);
                    if (latestYear == null || year > latestYear) {
                        latestYear = year;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid year format: {}", yearStr);
                }
            }
        }

        return latestYear;
    }

    public List<CountryInfo> getAllCountries() {
        return dataLoader.getCountryNames().entrySet().stream()
                .map(entry -> new CountryInfo(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .collect(Collectors.toList());
    }

    public String findIso3ByName(String countryName) {
        return getAllCountries().stream()
                .filter(c -> c.getName().equalsIgnoreCase(countryName) || 
                            c.getName().toLowerCase().contains(countryName.toLowerCase()))
                .map(CountryInfo::getIso3)
                .findFirst()
                .orElse(null);
    }
}

