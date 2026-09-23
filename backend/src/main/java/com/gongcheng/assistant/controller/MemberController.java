package com.gongcheng.assistant.controller;

import com.gongcheng.assistant.dto.AddMemberRequest;
import com.gongcheng.assistant.dto.ApiResponse;
import com.gongcheng.assistant.dto.SetRoleRequest;
import com.gongcheng.assistant.dto.UpdateMemberRequest;
import com.gongcheng.assistant.entity.Member;
import com.gongcheng.assistant.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
@Tag(name = "成员管理", description = "军团成员的增删改查、角色管理与数据刷新")
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    @Operation(summary = "添加成员", description = "输入接口链接，自动拉取并解析角色数据后添加到军团")
    public ApiResponse<Member> add(@Valid @RequestBody AddMemberRequest request) {
        Member member = memberService.addMember(request);
        return ApiResponse.success(member);
    }

    @PostMapping("/list")
    @Operation(summary = "成员列表", description = "查询指定军团下的所有成员")
    public ApiResponse<List<Member>> list(@RequestBody java.util.Map<String, Object> body) {
        Long legionId = Long.valueOf(body.get("legionId").toString());
        List<Member> members = memberService.listMembers(legionId);
        return ApiResponse.success(members);
    }

    @PostMapping("/my")
    @Operation(summary = "查询当前用户成员记录", description = "根据军团ID和用户ID查询该用户在军团中的成员记录，未绑定返回null")
    public ApiResponse<Member> getMyMember(@RequestBody java.util.Map<String, Object> body) {
        Long legionId = Long.valueOf(body.get("legionId").toString());
        String userId = (String) body.get("userId");
        Member member = memberService.getMyMember(legionId, userId);
        return ApiResponse.success(member);
    }

    @PostMapping("/{id}")
    @Operation(summary = "成员详情", description = "根据成员ID查询详细信息，本人/管理员/团长可查看梦游社链接")
    public ApiResponse<Member> get(@PathVariable Long id,
                                   @RequestBody java.util.Map<String, Object> body) {
        String operatorUserId = (String) body.get("operatorUserId");
        Member member = memberService.getMember(id, operatorUserId);
        return ApiResponse.success(member);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改成员链接", description = "修改成员的接口链接并重新拉取数据，仅本人或团长/管理员可操作")
    public ApiResponse<Member> update(@PathVariable Long id,
                                      @Valid @RequestBody UpdateMemberRequest request) {
        Member member = memberService.updateMember(id, request.getApiUrl(), request.getOperatorUserId());
        return ApiResponse.success(member);
    }

    @PostMapping("/{id}/refresh")
    @Operation(summary = "刷新成员数据", description = "用当前接口链接重新拉取最新数据，仅本人或团长/管理员可操作")
    public ApiResponse<Member> refresh(@PathVariable Long id,
                                      @RequestBody java.util.Map<String, Object> body) {
        String operatorUserId = (String) body.get("operatorUserId");
        Member member = memberService.refreshMember(id, operatorUserId);
        return ApiResponse.success(member);
    }

    @PostMapping("/sync-all")
    @Operation(summary = "一键同步所有成员", description = "团长或管理员权限，批量刷新军团所有成员数据")
    public ApiResponse<Integer> syncAll(@RequestBody java.util.Map<String, Object> body) {
        Long legionId = Long.valueOf(body.get("legionId").toString());
        String operatorUserId = (String) body.get("operatorUserId");
        int count = memberService.syncAll(legionId, operatorUserId);
        return ApiResponse.success(count);
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "设置成员角色", description = "仅军团所有人可操作，设置 admin 或 member")
    public ApiResponse<Member> setRole(@PathVariable Long id,
                                       @Valid @RequestBody SetRoleRequest request) {
        Member member = memberService.setRole(id, request.getOperatorUserId(), request.getRole());
        return ApiResponse.success(member);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除成员", description = "军团所有人或管理员可删除成员，不能删除所有人")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestBody java.util.Map<String, Object> body) {
        String operatorUserId = (String) body.get("operatorUserId");
        memberService.deleteMember(id, operatorUserId);
        return ApiResponse.success("删除成功", null);
    }
}
