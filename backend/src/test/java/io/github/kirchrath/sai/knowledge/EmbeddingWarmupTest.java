package io.github.kirchrath.sai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.ai.transformers.TransformersEmbeddingModel;

class EmbeddingWarmupTest {

    @Test
    void initializesModelEmbedsOnceAndCloses() throws Exception {
        try (MockedConstruction<TransformersEmbeddingModel> models = mockConstruction(TransformersEmbeddingModel.class,
                (model, context) -> when(model.embed(anyList())).thenReturn(List.of(new float[384])))) {

            EmbeddingWarmup.main(new String[0]);

            assertThat(models.constructed()).hasSize(1);
            TransformersEmbeddingModel model = models.constructed().getFirst();
            String expectedCache = System.getenv().getOrDefault("SAI_MODEL_CACHE", "/opt/sai/onnx");
            verify(model).setResourceCacheDirectory(expectedCache);
            verify(model).afterPropertiesSet();
            verify(model).embed(List.of("Warm-up"));
            verify(model).close();
        }
    }
}
