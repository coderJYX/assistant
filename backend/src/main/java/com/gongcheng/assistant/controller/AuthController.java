package com.gongcheng.assistant.controller;

import com.gongcheng.assistant.dto.ApiResponse;
import com.gongcheng.assistant.dto.WxLoginRequest;
import com.gongcheng.assistant.service.WxAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "微信登录认证")
public class AuthController {

    private final WxAuthService wxAuthService;

    @PostMapping("/wx-login")
    @Operation(summary = "微信登录", description = "用 wx.login 获取的 code 换取 openid")
    public ApiResponse<Map<String, String>> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        Map<String, String> result = wxAuthService.login(request.getCode());
        return ApiResponse.success(result);
    }
}
