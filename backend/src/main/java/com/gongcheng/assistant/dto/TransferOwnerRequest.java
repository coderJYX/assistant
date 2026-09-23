package com.gongcheng.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "转移团长请求")
public class TransferOwnerRequest {

    @NotBlank(message = "操作人用户ID不能为空")
    @Schema(description = "当前团长用户ID", example = "o_abc123")
    private String operatorUserId;

    @NotNull(message = "目标成员ID不能为空")
    @Schema(description = "目标成员ID（新团长）", example = "5")
    private Long targetMemberId;
}
