package com.jn.music.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileServerPropertiesTest {

    @Test
    void separatesInternalAndPublicUrls() {
        FileServerProperties properties = new FileServerProperties();
        properties.setPublicBaseUrl("http://jn_file.88933.vip:27472/");
        properties.setInternalBaseUrl("http://127.0.0.1:27472/");

        assertThat(properties.publicUrl("/audio/T0000001.mp3"))
                .isEqualTo("http://jn_file.88933.vip:27472/audio/T0000001.mp3");
        assertThat(properties.internalUrl("audio/T0000001.mp3"))
                .isEqualTo("http://127.0.0.1:27472/audio/T0000001.mp3");
    }
}
