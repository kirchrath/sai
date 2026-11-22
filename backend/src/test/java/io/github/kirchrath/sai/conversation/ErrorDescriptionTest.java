package io.github.kirchrath.sai.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorDescriptionTest {

    @Test
    void includesRootCause() {
        var e = new RuntimeException("Request failed", new IllegalStateException("x", new java.io.IOException("401 Unauthorized")));
        assertThat(ConversationController.describe(e)).isEqualTo("Request failed (401 Unauthorized)");
    }

    @Test
    void usesClassNameWithoutMessage() {
        assertThat(ConversationController.describe(new NullPointerException())).isEqualTo("NullPointerException");
    }
}
