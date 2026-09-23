package com.gongcheng.assistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc（Swagger）接口文档配置
 * 访问地址：http://localhost:8080/swagger-ui.html
 */
@Configuration
public class SpringDocConfig {

    /**
     * 自定义OpenAPI文档信息
     * 配置接口标题、版本、描述和联系人
     *
     * @return OpenAPI配置
     */
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