package io.github.kirchrath.sai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;

/** Absicherung gegen inkompatible Tika-/commons-compress-Versionen. */
class TikaExtractionTest {

    @ParameterizedTest
    @ValueSource(strings = {"notiz.md", "notiz.txt", "seite.html"})
    void extractsTextFromCommonFormats(String filename) {
        byte[] bytes = "<p>Der Wartungsslot ist dienstags.</p>".getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        assertThat(new TikaDocumentReader(resource).get())
                .singleElement()
                .satisfies(doc -> assertThat(doc.getText()).contains("Wartungsslot ist dienstags"));
    }
}
