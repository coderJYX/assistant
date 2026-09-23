package com.gongcheng.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 攻城助手后端启动类
 * Spring Boot 应用入口
 */
@SpringBootApplication
public class AssistantApplication {

    /**
     * 应用启动方法
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AssistantApplication.class, args);
    }
}