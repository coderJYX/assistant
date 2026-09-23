package com.gongcheng.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "更新成员请求")
public class UpdateMemberRequest {

    @NotBlank(message = "接口链接不能为空")
    @Schema(description = "新的接口链接", example = "https://api.example.com/player?uid=xxx")
    private String apiUrl;

    @NotBlank(message = "操作人ID不能为空")
    @Schema(description = "操作人用户ID")
    private String operatorUserId;
}
