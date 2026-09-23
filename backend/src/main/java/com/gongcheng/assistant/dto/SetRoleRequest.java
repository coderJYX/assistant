package com.gongcheng.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "设置成员角色请求")
public class SetRoleRequest {

    @NotBlank(message = "操作用户ID不能为空")
    @Schema(description = "操作用户ID（必须是军团所有人）", example = "u_abc123")
    private String operatorUserId;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(admin|member)$", message = "角色只能是 admin 或 member")
    @Schema(description = "目标角色：admin/member", example = "admin")
    private String role;
}
