package com.gongcheng.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "加入军团请求")
public class JoinLegionRequest {

    @NotBlank(message = "军团口令不能为空")
    @Size(min = 4, max = 32, message = "口令长度4-32位")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9]+$", message = "口令只能包含中文、英文字母和数字")
    @Schema(description = "军团口令", example = "军团2024")
    private String code;

    @NotBlank(message = "用户ID不能为空")
    @Schema(description = "加入人用户ID", example = "u_abc123")
    private String userId;

    @Schema(description = "接口链接（邀请加入时必填，加入后自动创建成员）", example = "https://api.example.com/xxx")
    private String apiUrl;
}
