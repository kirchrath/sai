package io.github.kirchrath.sai.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import io.github.kirchrath.sai.config.SaiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

@Component
public class WebFetchTools {

    private static final Logger log = LoggerFactory.getLogger(WebFetchTools.class);
    private static final int MAX_REDIRECTS = 5;

    private final SaiProperties.WebFetch config;
    private final UrlGuard guard;
    private final HttpClient httpClient;

    public WebFetchTools(SaiProperties properties) {
        this.config = properties.webFetch();
        this.guard = new UrlGuard(config.allowPrivateNetworks(), config.blockedHosts());
        // Redirects werden manuell verfolgt, damit jedes Ziel geprueft wird.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Tool(name = "web_fetch",
            description = "Ruft eine oeffentliche Webseite oder Datei (http/https) ab und liefert den extrahierten Text.")
    public String fetch(@ToolParam(description = "Vollstaendige URL inkl. https://") String url) {
        try {
            URI uri = guard.check(url);
            for (int i = 0; i <= MAX_REDIRECTS; i++) {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(config.timeout())
                        .header("User-Agent", "sai-agent/1.0")
                        .header("Accept", "text/html,application/xhtml+xml,text/plain,application/pdf,*/*;q=0.5")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    response.body().close();
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new IllegalArgumentException("Weiterleitung ohne Ziel"));
                    uri = guard.check(uri.resolve(location).toString());
                    continue;
                }
                byte[] body;
                try (InputStream in = response.body()) {
                    body = in.readNBytes(config.maxBytes());
                }
                if (status >= 400) {
                    return "HTTP-Fehler " + status + " beim Abruf von " + uri;
                }
                return truncate(extractText(body, uri));
            }
            return "Zu viele Weiterleitungen";
        } catch (IllegalArgumentException e) {
            return "Abruf nicht erlaubt: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Abruf abgebrochen";
        } catch (IOException | RuntimeException e) {
            log.debug("Web-Fetch fehlgeschlagen fuer {}", url, e);
            return "Abruf fehlgeschlagen: " + e.getMessage();
        }
    }

    private String extractText(byte[] body, URI uri) {
        String name = uri.getPath() == null || uri.getPath().isBlank() ? "index.html" : uri.getPath();
        ByteArrayResource resource = new ByteArrayResource(body) {
            @Override
            public String getFilename() {
                return name.substring(name.lastIndexOf('/') + 1);
            }
        };
        List<Document> documents = new TikaDocumentReader(resource).get();
        StringBuilder text = new StringBuilder();
        for (Document document : documents) {
            if (document.getText() != null) {
                text.append(document.getText()).append('\n');
            }
        }
        return text.toString().replaceAll("\\n{3,}", "\n\n").strip();
    }

    private String truncate(String text) {
        if (text.length() <= config.maxChars()) {
            return text;
        }
        return text.substring(0, config.maxChars()) + "\n[... gekuerzt]";
    }
}
