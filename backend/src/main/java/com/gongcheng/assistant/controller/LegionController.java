package com.gongcheng.assistant.controller;

import com.gongcheng.assistant.dto.ApiResponse;
import com.gongcheng.assistant.dto.CreateLegionRequest;
import com.gongcheng.assistant.dto.JoinLegionRequest;
import com.gongcheng.assistant.dto.JoinLegionResponse;
import com.gongcheng.assistant.dto.TransferOwnerRequest;
import com.gongcheng.assistant.entity.Legion;
import com.gongcheng.assistant.service.LegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/legion")
@RequiredArgsConstructor
@Tag(name = "军团管理", description = "军团的创建、加入、查询、转移、退出")
public class LegionController {

    private final LegionService legionService;

    @PostMapping("/create")
    @Operation(summary = "创建军团", description = "设置军团名称和口令，创建新军团，创建人为军团所有人")
    public ApiResponse<Legion> create(@Valid @RequestBody CreateLegionRequest request) {
        Legion legion = legionService.createLegion(request);
        return ApiResponse.success(legion);
    }

    @PostMapping("/join")
    @Operation(summary = "加入军团", description = "通过口令加入已有军团；携带 apiUrl 时加入后自动创建成员")
    public ApiResponse<JoinLegionResponse> join(@Valid @RequestBody JoinLegionRequest request) {
        JoinLegionResponse response = legionService.joinLegion(request);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询军团信息", description = "根据军团ID查询军团详情")
    public ApiResponse<Legion> get(@PathVariable Long id) {
        Legion legion = legionService.getLegion(id);
        return ApiResponse.success(legion);
    }

    @PostMapping("/my")
    @Operation(summary = "查询当前用户所在军团", description = "根据用户ID查询其所在军团（团长或成员），未加入返回null")
    public ApiResponse<Legion> getMy(@RequestBody java.util.Map<String, Object> body) {
        String userId = (String) body.get("userId");
        Legion legion = legionService.getMyLegion(userId);
        return ApiResponse.success(legion);
    }

    @PostMapping("/{id}/transfer")
    @Operation(summary = "转移团长", description = "仅当前团长可操作，将团长转移给指定成员")
    public ApiResponse<Legion> transfer(@PathVariable Long id,
                                        @Valid @RequestBody TransferOwnerRequest request) {
        Legion legion = legionService.transferOwner(id, request.getOperatorUserId(), request.getTargetMemberId());
        return ApiResponse.success(legion);
    }

    @PostMapping("/{id}/exit")
    @Operation(summary = "退出军团", description = "删除当前用户的成员记录；团长需先转移或军团只剩自己时解散")
    public ApiResponse<Void> exit(@PathVariable Long id,
                                  @RequestBody java.util.Map<String, Object> body) {
        String userId = (String) body.get("userId");
        legionService.exitLegion(id, userId);
        return ApiResponse.success("退出成功", null);
    }
}
