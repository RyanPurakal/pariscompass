package com.ryanpurakal.pariscompass.service;

import com.ryanpurakal.pariscompass.model.CountryInfo;
import com.ryanpurakal.pariscompass.model.CountryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountryMetricsServiceTest {

    @Mock
    private DataLoader dataLoader;

    @InjectMocks
    private CountryMetricsService service;

    private Map<String, String> countryNames;
    private Map<String, Map<String, DataLoader.Co2Data>> co2Data;
    private Map<String, Map<String, DataLoader.RenewablesData>> renewablesData;
    private Map<String, Map<String, DataLoader.TemperatureData>> temperatureData;

    @BeforeEach
    void setUp() {
        countryNames = new HashMap<>();
        countryNames.put("USA", "United States");
        countryNames.put("IND", "India");

        co2Data = new HashMap<>();
        Map<String, DataLoader.Co2Data> usaCo2 = new HashMap<>();
        usaCo2.put("2022", new DataLoader.Co2Data(4713.0, 14.2));
        co2Data.put("USA", usaCo2);

        renewablesData = new HashMap<>();
        Map<String, DataLoader.RenewablesData> usaRenewables = new HashMap<>();
        usaRenewables.put("2022", new DataLoader.RenewablesData(21.5));
        renewablesData.put("USA", usaRenewables);

        temperatureData = new HashMap<>();
        Map<String, DataLoader.TemperatureData> usaTemp = new HashMap<>();
        usaTemp.put("2022", new DataLoader.TemperatureData(1.2));
        temperatureData.put("USA", usaTemp);

        when(dataLoader.getCountryNames()).thenReturn(countryNames);
        when(dataLoader.getCo2Data()).thenReturn(co2Data);
        when(dataLoader.getRenewablesData()).thenReturn(renewablesData);
        when(dataLoader.getTemperatureData()).thenReturn(temperatureData);
    }

    @Test
    void testGetLatestMetrics_ValidCountry() {
        CountryMetrics metrics = service.getLatestMetrics("USA");

        assertNotNull(metrics);
        assertEquals("USA", metrics.getIso3());
        assertEquals("United States", metrics.getName());
        assertEquals(2022, metrics.getYear());
        assertEquals(14.2, metrics.getCo2PerCapita(), 0.01);
        assertEquals(4713.0, metrics.getCo2TotalMt(), 0.01);
        assertEquals(21.5, metrics.getRenewablesSharePct(), 0.01);
        assertEquals(1.2, metrics.getTemperatureAnomalyC(), 0.01);
        assertNotNull(metrics.getSource());
    }

    @Test
    void testGetLatestMetrics_InvalidCountry() {
        CountryMetrics metrics = service.getLatestMetrics("XXX");
        assertNull(metrics);
    }

    @Test
    void testGetLatestMetrics_NullIso3() {
        CountryMetrics metrics = service.getLatestMetrics(null);
        assertNull(metrics);
    }

    @Test
    void testGetLatestMetrics_EmptyIso3() {
        CountryMetrics metrics = service.getLatestMetrics("");
        assertNull(metrics);
    }

    @Test
    void testGetAllCountries() {
        List<CountryInfo> countries = service.getAllCountries();

        assertNotNull(countries);
        assertEquals(2, countries.size());
        assertEquals("India", countries.get(0).getName()); // Sorted alphabetically
        assertEquals("IND", countries.get(0).getIso3());
        assertEquals("United States", countries.get(1).getName());
        assertEquals("USA", countries.get(1).getIso3());
    }

    @Test
    void testGetLatestMetrics_PartialData() {
        // Test with country that has only CO2 data
        Map<String, DataLoader.Co2Data> indCo2 = new HashMap<>();
        indCo2.put("2022", new DataLoader.Co2Data(2695.0, 1.9));
        co2Data.put("IND", indCo2);
        countryNames.put("IND", "India");

        CountryMetrics metrics = service.getLatestMetrics("IND");

        assertNotNull(metrics);
        assertEquals("IND", metrics.getIso3());
        assertEquals("India", metrics.getName());
        assertEquals(1.9, metrics.getCo2PerCapita(), 0.01);
        // Other fields should be null if not present
        assertNull(metrics.getRenewablesSharePct());
        assertNull(metrics.getTemperatureAnomalyC());
    }
}

