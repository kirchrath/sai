package io.github.kirchrath.sai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlGuardTest {

    private final UrlGuard guard = new UrlGuard(false, Set.of("blocked.example"));

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/", "http://localhost:8080/", "http://10.0.0.1/", "http://192.168.1.1/",
            "http://172.16.0.5/", "http://169.254.169.254/latest/meta-data", "http://[::1]/", "http://0.0.0.0/",
            "http://100.64.0.1/", "http://[fd00::1]/"})
    void blocksInternalAddresses(String url) {
        assertThatThrownBy(() -> guard.check(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com/", "http://user:pw@example.com/",
            "http://blocked.example/", "not a url"})
    void blocksDisallowedUrls(String url) {
        assertThatThrownBy(() -> guard.check(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsPublicAddresses() throws Exception {
        assertThat(UrlGuard.isInternal(InetAddress.getByName("93.184.215.14"))).isFalse();
        assertThat(UrlGuard.isInternal(InetAddress.getByName("2606:2800:21f:cb07:6820:80da:af6b:8b2c"))).isFalse();
    }

    @Test
    void privateNetworksCanBeAllowed() {
        assertThat(new UrlGuard(true, Set.of()).check("http://127.0.0.1:9/x").getHost()).isEqualTo("127.0.0.1");
    }
}
