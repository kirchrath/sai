package io.github.kirchrath.sai.knowledge;

import java.util.List;

import org.springframework.ai.transformers.TransformersEmbeddingModel;

/**
 * Laedt ONNX-Modell, Tokenizer und die DJL-Native-Bibliotheken einmalig in die Caches
 * ({@code SAI_MODEL_CACHE}, {@code DJL_CACHE_DIR}). Wird beim Docker-Build ausgefuehrt,
 * damit der Container zur Laufzeit keine Downloads mehr benoetigt.
 */
public final class EmbeddingWarmup {

    private EmbeddingWarmup() {
    }

    public static void main(String[] args) throws Exception {
        String cacheDir = System.getenv().getOrDefault("SAI_MODEL_CACHE", "/opt/sai/onnx");
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setResourceCacheDirectory(cacheDir);
        model.afterPropertiesSet();
        List<float[]> vectors = model.embed(List.of("Warm-up"));
        System.out.printf("Embedding-Warm-up erfolgreich: %d Dimensionen, Cache %s%n", vectors.get(0).length, cacheDir);
        model.close();
    }
}
