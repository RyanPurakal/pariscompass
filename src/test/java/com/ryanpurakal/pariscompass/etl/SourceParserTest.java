package com.ryanpurakal.pariscompass.etl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceParserTest {
    private static final Set<String> ISO = Set.copyOf(Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA3));
    private static final int MAX_YEAR = 2025;

    private static ParseResult parseFixture(String name, DataSource source) throws IOException {
        try (Reader r = new InputStreamReader(Objects.requireNonNull(
                SourceParserTest.class.getResourceAsStream("/etl/" + name)), StandardCharsets.UTF_8)) {
            return SourceParser.parse(r, source, ISO, MAX_YEAR);
        }
    }

    private static Map<RejectionReason, Long> byReason(ParseResult r) {
        return r.rejections().stream().collect(java.util.stream.Collectors.groupingBy(
                Rejection::reason, java.util.stream.Collectors.counting()));
    }

    @Test
    void co2FixtureClassifiesEveryRow() throws IOException {
        ParseResult r = parseFixture("co2.csv", DataSource.OWID_CO2);

        assertThat(r.rowsRead()).isEqualTo(11);
        assertThat(r.rowsSkippedAggregate()).isEqualTo(1);
        assertThat(r.rowsSkippedNonIso()).isEqualTo(1);
        assertThat(r.rowsRejected()).isEqualTo(4);
        assertThat(r.accepted()).hasSize(14);
        assertThat(r.valuesRejected()).isEqualTo(2);
        assertThat(byReason(r)).containsExactlyInAnyOrderEntriesOf(Map.of(
                RejectionReason.UNKNOWN_ISO3, 1L,
                RejectionReason.DUPLICATE_ROW, 1L,
                RejectionReason.INVALID_YEAR, 1L,
                RejectionReason.YEAR_OUT_OF_RANGE, 1L,
                RejectionReason.OUT_OF_RANGE, 1L,
                RejectionReason.NON_NUMERIC, 1L));
        assertThat(r.countryNames()).containsOnlyKeys("USA", "SXM", "DEU", "COD");
    }

    @Test
    void outlierIsRejectedButRestOfRowIsKept() throws IOException {
        ParseResult r = parseFixture("co2.csv", DataSource.OWID_CO2);

        Rejection sxm = r.rejections().stream().filter(x -> "SXM".equals(x.iso3())).findFirst().orElseThrow();
        assertThat(sxm.reason()).isEqualTo(RejectionReason.OUT_OF_RANGE);
        assertThat(sxm.metricCode()).isEqualTo("co2_per_capita_t");
        assertThat(sxm.rawValue()).isEqualTo("782.743");
        assertThat(sxm.lineNumber()).isEqualTo(8);
        assertThat(r.accepted()).filteredOn(o -> o.iso3().equals("SXM"))
                .extracting(ParsedObservation::metricCode)
                .containsExactlyInAnyOrder("co2_total_mt", "population");
    }

    @Test
    void negativeGhgIsAcceptedForNetSinks() throws IOException {
        ParseResult r = parseFixture("co2.csv", DataSource.OWID_CO2);
        assertThat(r.accepted()).contains(new ParsedObservation("COD", "ghg_total_mt", 1911, -19.725));
    }

    @Test
    void duplicateKeepsFirstOccurrence() throws IOException {
        ParseResult r = parseFixture("co2.csv", DataSource.OWID_CO2);
        assertThat(r.accepted()).contains(new ParsedObservation("USA", "co2_total_mt", 2024, 4904.12));
        assertThat(r.accepted()).doesNotContain(new ParsedObservation("USA", "co2_total_mt", 2024, 1.0));
    }

    @Test
    void literalNaNIsRejectedAsNonNumeric() throws IOException {
        ParseResult r = parseFixture("energy.csv", DataSource.OWID_ENERGY);
        assertThat(r.rejections()).anySatisfy(x -> {
            assertThat(x.reason()).isEqualTo(RejectionReason.NON_NUMERIC);
            assertThat(x.rawValue()).isEqualTo("NaN");
        });
        assertThat(r.accepted()).hasSize(12);
        assertThat(r.valuesRejected()).isEqualTo(2);
    }

    @Test
    void upperBoundIsInclusive() throws IOException {
        ParseResult r = parseFixture("energy.csv", DataSource.OWID_ENERGY);
        assertThat(r.accepted()).contains(new ParsedObservation("UGA", "renewables_share_elec_pct", 2013, 100.0));
    }

    @Test
    void handlesByteOrderMarkAndOwidNonIsoCodes() throws IOException {
        ParseResult r = parseFixture("temperature.csv", DataSource.OWID_TEMPERATURE);
        assertThat(r.rowsSkippedNonIso()).isEqualTo(2);
        assertThat(r.accepted()).hasSize(3);
        assertThat(r.valuesRejected()).isEqualTo(1);
        // TJK's only value was rejected, so it must not create a country.
        assertThat(r.countryNames()).containsOnlyKeys("USA", "NOR");
    }

    @Test
    void missingExpectedColumnFailsTheSource() {
        Reader csv = new StringReader("country,year,iso_code,co2\nUnited States,2024,USA,1\n");
        assertThatThrownBy(() -> SourceParser.parse(csv, DataSource.OWID_CO2, ISO, MAX_YEAR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("co2_per_capita");
    }

    @Test
    void emptyFileFailsTheSource() {
        assertThatThrownBy(() -> SourceParser.parse(new StringReader(""), DataSource.OWID_CO2, ISO, MAX_YEAR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }
}
