package com.gongcheng.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "添加成员请求")
public class AddMemberRequest {

    @NotNull(message = "军团ID不能为空")
    @Schema(description = "所属军团ID", example = "1")
    private Long legionId;

    @NotBlank(message = "接口链接不能为空")
    @Schema(description = "角色数据接口链接", example = "https://api.example.com/player?uid=xxx")
    private String apiUrl;

    @Schema(description = "操作用户ID（用于权限校验）", example = "u_abc123")
    private String userId;
}
