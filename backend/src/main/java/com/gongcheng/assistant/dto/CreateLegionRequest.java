package com.gongcheng.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建军团请求")
public class CreateLegionRequest {

    @NotBlank(message = "军团名称不能为空")
    @Size(max = 50, message = "军团名称最长50字")
    @Schema(description = "军团名称", example = "先锋军团")
    private String name;

    @NotBlank(message = "军团口令不能为空")
    @Size(min = 4, max = 32, message = "口令长度4-32位")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9]+$", message = "口令只能包含中文、英文字母和数字")
    @Schema(description = "军团口令（用于他人加入，仅限中英文数字）", example = "军团2024")
    private String code;

    @NotBlank(message = "用户ID不能为空")
    @Schema(description = "创建人用户ID", example = "u_abc123")
    private String userId;
}
