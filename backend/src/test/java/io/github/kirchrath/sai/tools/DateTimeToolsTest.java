package io.github.kirchrath.sai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DateTimeToolsTest {

    private final DateTimeTools tools = new DateTimeTools();

    @Test
    void defaultsToBerlin() {
        assertThat(tools.currentDateTime(null)).contains("Europe/Berlin");
        assertThat(tools.currentDateTime("  ")).contains("Europe/Berlin");
    }

    @Test
    void usesGivenZoneWithGermanWeekday() {
        assertThat(tools.currentDateTime(" UTC ")).endsWith(", UTC)")
                .containsAnyOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag");
    }

    @Test
    void reportsUnknownZone() {
        assertThat(tools.currentDateTime("Mars/Olympus")).isEqualTo("Unbekannte Zeitzone: Mars/Olympus");
    }
}
