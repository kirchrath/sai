package io.github.kirchrath.sai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.kirchrath.sai.config.SaiProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebFetchToolsTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, 200, "text/html",
                "<html><body><h1>Startseite</h1><p>Hallo Welt</p></body></html>"));
        server.createContext("/text.txt", exchange -> respond(exchange, 200, "text/plain", "x".repeat(50)));
        server.createContext("/weiter", exchange -> redirect(exchange, "/text.txt"));
        server.createContext("/kreis", exchange -> redirect(exchange, "/kreis"));
        server.createContext("/intern", exchange -> redirect(exchange, "http://localhost:1/"));
        server.createContext("/ohne-ziel", exchange -> {
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/fehlt", exchange -> respond(exchange, 404, "text/plain", "weg"));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static WebFetchTools tools(boolean allowPrivate, int maxChars) {
        SaiProperties properties = new SaiProperties(null, null, null,
                new SaiProperties.WebFetch(Duration.ofSeconds(5), 10_000, maxChars, allowPrivate, Set.of("localhost")),
                null);
        return new WebFetchTools(properties);
    }

    @Test
    void extractsTextFromHtmlWithoutPath() {
        assertThat(tools(true, 1000).fetch(base)).contains("Startseite").contains("Hallo Welt")
                .doesNotContain("<h1>");
    }

    @Test
    void followsRedirectsAndTruncates() {
        assertThat(tools(true, 10).fetch(base + "/weiter")).isEqualTo("xxxxxxxxxx\n[... gekuerzt]");
    }

    @Test
    void checksRedirectTargets() {
        assertThat(tools(true, 1000).fetch(base + "/intern")).isEqualTo("Abruf nicht erlaubt: Host ist gesperrt");
    }

    @Test
    void stopsAfterTooManyRedirects() {
        assertThat(tools(true, 1000).fetch(base + "/kreis")).isEqualTo("Zu viele Weiterleitungen");
    }

    @Test
    void redirectWithoutLocationIsRejected() {
        assertThat(tools(true, 1000).fetch(base + "/ohne-ziel"))
                .isEqualTo("Abruf nicht erlaubt: Weiterleitung ohne Ziel");
    }

    @Test
    void reportsHttpErrors() {
        assertThat(tools(true, 1000).fetch(base + "/fehlt")).startsWith("HTTP-Fehler 404");
    }

    @Test
    void blocksPrivateAddressesByDefault() {
        assertThat(tools(false, 1000).fetch(base)).startsWith("Abruf nicht erlaubt: Zugriff auf interne Adressen");
    }

    @Test
    void reportsConnectionFailures() {
        server.stop(0);

        assertThat(tools(true, 1000).fetch(base)).startsWith("Abruf fehlgeschlagen");
    }

    @Test
    void handlesInterruption() {
        Thread.currentThread().interrupt();
        try {
            assertThat(tools(true, 1000).fetch(base)).isEqualTo("Abruf abgebrochen");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
