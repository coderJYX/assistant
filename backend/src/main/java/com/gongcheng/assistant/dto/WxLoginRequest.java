package com.gongcheng.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信登录请求")
public class WxLoginRequest {

    @NotBlank(message = "code不能为空")
    @Schema(description = "wx.login 获取的临时登录凭证", example = "0a1b2c3d4e5f")
    private String code;
}
