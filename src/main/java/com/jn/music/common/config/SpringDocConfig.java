package com.jn.music.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JNMusic API")
                        .description("音乐播放器后端API文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("JNMusic")
                                .url("https://github.com/JNNarrator/JNMUSIC_SERVER")));
    }
}
