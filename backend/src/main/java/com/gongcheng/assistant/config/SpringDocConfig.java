package com.gongcheng.assistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("攻城助手 API")
                        .version("1.0.0")
                        .description("攻城助手微信小程序后端接口文档")
                        .contact(new Contact().name("攻城助手")));
    }
}
