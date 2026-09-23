package com.gongcheng.assistant.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.gongcheng.assistant.mapper")
public class MybatisPlusConfig {
    // MyBatis Plus 基础配置
    // 如需分页，可在此添加 PaginationInnerInterceptor（需引入 mybatis-plus-jsqlparser）
}
