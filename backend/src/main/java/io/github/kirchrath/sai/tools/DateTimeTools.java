package io.github.kirchrath.sai.tools;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DateTimeTools {

    @Tool(name = "current_datetime",
            description = "Liefert das aktuelle Datum und die aktuelle Uhrzeit in einer Zeitzone (IANA, z.B. Europe/Berlin).")
    public String currentDateTime(
            @ToolParam(required = false, description = "IANA-Zeitzone, Standard: Europe/Berlin") String timezone) {
        ZoneId zone;
        try {
            zone = timezone == null || timezone.isBlank() ? ZoneId.of("Europe/Berlin") : ZoneId.of(timezone.strip());
        } catch (DateTimeException e) {
            return "Unbekannte Zeitzone: " + timezone;
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        return "%s (%s, %s)".formatted(
                now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.GERMAN),
                zone.getId());
    }
}
