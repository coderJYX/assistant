package com.gongcheng.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gongcheng.assistant.common.GlobalErrorCodeConstants;
import com.gongcheng.assistant.common.SecurityValidator;
import com.gongcheng.assistant.common.ServiceException;
import com.gongcheng.assistant.dto.AddMemberRequest;
import com.gongcheng.assistant.entity.Legion;
import com.gongcheng.assistant.entity.Member;
import com.gongcheng.assistant.mapper.LegionMapper;
import com.gongcheng.assistant.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 军团成员服务
 * 负责成员的增删改查、角色管理、数据同步等业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final LegionMapper legionMapper;
    private final ApiQueryService apiQueryService;

    /**
     * 添加成员
     * 1. 校验用户ID格式
     * 2. 校验军团存在
     * 3. 校验同一军团下链接不重复
     * 4. 调用梦游社接口拉取角色数据
     * 5. 军团创建人绑定角色时自动设为团长，其他为普通成员
     *
     * @param request 添加成员请求（包含军团ID、用户ID、梦游社链接）
     * @return 新增的成员信息
     */
    public Member addMember(AddMemberRequest request) {
        // 安全校验：用户ID格式
        SecurityValidator.validateUserId(request.getUserId());

        // 校验军团存在
        Legion legion = legionMapper.selectById(request.getLegionId());
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        // 输入清洗：去除控制字符和首尾空格
        String apiUrl = SecurityValidator.sanitize(request.getApiUrl());

        // 校验同一军团下游魂社链接不重复（幂等性）
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, legion.getId())
                        .eq(Member::getApiUrl, apiUrl));
        if (count != null && count > 0) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_API_URL_EXISTS);
        }

        Member member = new Member();
        member.setLegionId(legion.getId());
        member.setUserId(request.getUserId());

        // 军团创建人绑定角色时默认为团长，其他为普通成员
        if (request.getUserId() != null && request.getUserId().equals(legion.getOwnerUserId())) {
            member.setRole("owner");
        } else {
            member.setRole("member");
        }

        // 调用梦游社接口拉取并解析角色数据，同时存储用户原始输入链接
        apiQueryService.fillMemberFromApi(member, apiUrl);

        memberMapper.insert(member);
        log.info("添加成员成功: id={}, roleName={}, legionId={}",
                member.getId(), member.getRoleName(), member.getLegionId());
        return member;
    }

    /**
     * 查询军团成员列表
     * 按 progress 中的关卡数字倒序排序（如 "229.大竞技场" -> 229）
     *
     * @param legionId 军团ID
     * @return 成员列表（按关卡倒序）
     */
    public List<Member> listMembers(Long legionId) {
        // 校验军团存在
        Legion legion = legionMapper.selectById(legionId);
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        List<Member> members = memberMapper.selectList(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, legionId));

        // 按 progress 中的数字倒序排序
        members.sort((a, b) -> {
            int pa = extractProgressNumber(a.getProgress());
            int pb = extractProgressNumber(b.getProgress());
            return Integer.compare(pb, pa);
        });

        return members;
    }

    /**
     * 从 progress 字符串中提取开头的数字
     * 例如 "229.大竞技场" -> 229，"大竞技场" -> 0
     *
     * @param progress 进度字符串
     * @return 关卡数字，无法提取时返回0
     */
    private int extractProgressNumber(String progress) {
        if (progress == null || progress.isEmpty()) return 0;
        StringBuilder sb = new StringBuilder();
        for (char c : progress.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            } else if (sb.length() > 0) {
                // 遇到非数字且已收集到数字，停止提取
                break;
            }
        }
        try {
            return sb.length() > 0 ? Integer.parseInt(sb.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 查询用户在指定军团中的成员记录
     * 用于判断用户是否已绑定角色
     *
     * @param legionId 军团ID
     * @param userId   用户ID
     * @return 成员记录，未绑定返回 null
     */
    public Member getMyMember(Long legionId, String userId) {
        return memberMapper.selectOne(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, legionId)
                        .eq(Member::getUserId, userId)
                        .last("LIMIT 1"));
    }

    /**
     * 查询成员详情
     * 权限控制：本人、管理员、团长可以查看梦游社链接；
     * 普通成员查看别人时，apiUrl 设为 null 不返回
     *
     * @param id             成员ID
     * @param operatorUserId 操作人用户ID（用于权限判断）
     * @return 成员详情（链接字段按权限返回）
     */
    public Member getMember(Long id, String operatorUserId) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_NOT_FOUND);
        }

        // 权限判断：本人、管理员、团长可以查看梦游社链接
        boolean canSeeUrl = false;

        // 1. 本人可以查看自己的链接
        if (operatorUserId != null && operatorUserId.equals(member.getUserId())) {
            canSeeUrl = true;
        } else {
            // 2. 查询操作人在该军团中的角色，判断是否为管理员或团长
            Member operator = memberMapper.selectOne(
                    new LambdaQueryWrapper<Member>()
                            .eq(Member::getLegionId, member.getLegionId())
                            .eq(Member::getUserId, operatorUserId)
                            .last("LIMIT 1"));
            if (operator != null && ("admin".equals(operator.getRole()) || "owner".equals(operator.getRole()))) {
                canSeeUrl = true;
            }
        }

        // 无权限时不返回梦游社链接
        if (!canSeeUrl) {
            member.setApiUrl(null);
        }
        return member;
    }

    /**
     * 修改成员梦游社链接
     * 权限：本人、团长、管理员可操作
     * 修改后重新调用接口拉取最新数据
     *
     * @param id             成员ID
     * @param apiUrl         新的梦游社链接
     * @param operatorUserId 操作人用户ID
     * @return 更新后的成员信息
     */
    public Member updateMember(Long id, String apiUrl, String operatorUserId) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_NOT_FOUND);
        }

        // 权限校验：本人、团长或管理员才能修改
        checkSelfOrPrivileged(member, operatorUserId);

        // 输入清洗
        String trimmedUrl = SecurityValidator.sanitize(apiUrl);

        // 校验同一军团下链接不重复（排除自己）
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, member.getLegionId())
                        .eq(Member::getApiUrl, trimmedUrl)
                        .ne(Member::getId, id));
        if (count != null && count > 0) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_API_URL_EXISTS);
        }

        // 用新链接重新拉取数据
        apiQueryService.fillMemberFromApi(member, trimmedUrl);

        memberMapper.updateById(member);
        log.info("更新成员成功: id={}, roleName={}", member.getId(), member.getRoleName());
        return member;
    }

    /**
     * 刷新成员数据
     * 用已存储的梦游社链接重新拉取最新数据
     * 权限：本人、团长、管理员可操作
     *
     * @param id             成员ID
     * @param operatorUserId 操作人用户ID
     * @return 刷新后的成员信息
     */
    public Member refreshMember(Long id, String operatorUserId) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_NOT_FOUND);
        }

        // 权限校验：本人、团长或管理员才能刷新
        checkSelfOrPrivileged(member, operatorUserId);

        // 用已存储的链接重新拉取数据
        apiQueryService.fillMemberFromApi(member, member.getApiUrl());

        memberMapper.updateById(member);
        log.info("刷新成员成功: id={}, roleName={}", member.getId(), member.getRoleName());
        return member;
    }

    /**
     * 权限校验：操作人必须是本人、团长或军团管理员
     * 额外限制：管理员不能修改其他管理员和团长的信息
     * 不满足时抛出无权限异常
     *
     * @param target         目标成员
     * @param operatorUserId 操作人用户ID
     */
    private void checkSelfOrPrivileged(Member target, String operatorUserId) {
        if (operatorUserId == null) {
            throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
        }

        // 1. 本人操作放行
        if (operatorUserId.equals(target.getUserId())) {
            return;
        }

        // 2. 团长放行（团长可以修改所有人）
        Legion legion = legionMapper.selectById(target.getLegionId());
        if (legion != null && operatorUserId.equals(legion.getOwnerUserId())) {
            return;
        }

        // 3. 查询操作人角色
        Member operator = memberMapper.selectOne(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, target.getLegionId())
                        .eq(Member::getUserId, operatorUserId));

        // 4. 管理员只能修改普通成员，不能修改其他管理员和团长
        if (operator != null && "admin".equals(operator.getRole())) {
            if ("admin".equals(target.getRole()) || "owner".equals(target.getRole())) {
                // 管理员不能修改其他管理员或团长
                throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
            }
            return;
        }

        throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
    }

    /**
     * 一键同步军团所有成员数据
     * 权限：仅团长或管理员可操作
     * 逐个调用梦游社接口刷新，单个失败不影响其他成员
     *
     * @param legionId       军团ID
     * @param operatorUserId 操作人用户ID
     * @return 成功同步的成员数量
     */
    public int syncAll(Long legionId, String operatorUserId) {
        // 校验军团存在
        Legion legion = legionMapper.selectById(legionId);
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        // 校验操作人是团长或管理员
        boolean isOwner = operatorUserId != null && operatorUserId.equals(legion.getOwnerUserId());
        boolean isAdmin = false;
        if (!isOwner && operatorUserId != null) {
            Member operator = memberMapper.selectOne(
                    new LambdaQueryWrapper<Member>()
                            .eq(Member::getLegionId, legionId)
                            .eq(Member::getUserId, operatorUserId));
            isAdmin = operator != null && "admin".equals(operator.getRole());
        }
        if (!isOwner && !isAdmin) {
            throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
        }

        List<Member> members = memberMapper.selectList(
                new LambdaQueryWrapper<Member>().eq(Member::getLegionId, legionId));

        // 逐个同步，单个失败不影响整体
        int success = 0;
        for (Member m : members) {
            try {
                apiQueryService.fillMemberFromApi(m, m.getApiUrl());
                memberMapper.updateById(m);
                success++;
            } catch (Exception e) {
                log.warn("同步成员失败: id={}, roleName={}, err={}", m.getId(), m.getRoleName(), e.getMessage());
            }
        }
        log.info("一键同步完成: legionId={}, 成功{}/{}, operator={}", legionId, success, members.size(), operatorUserId);
        return success;
    }

    /**
     * 设置成员角色（设为管理员或取消管理员）
     * 权限：仅团长可操作
     * 不能修改团长自己的角色
     *
     * @param memberId       目标成员ID
     * @param operatorUserId 操作人用户ID（必须是团长）
     * @param targetRole     目标角色（admin/member）
     * @return 更新后的成员信息
     */
    public Member setRole(Long memberId, String operatorUserId, String targetRole) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_NOT_FOUND);
        }

        // 校验操作人是团长
        Legion legion = legionMapper.selectById(member.getLegionId());
        if (legion == null || !operatorUserId.equals(legion.getOwnerUserId())) {
            throw new ServiceException(GlobalErrorCodeConstants.ONLY_OWNER_CAN_SET_ADMIN);
        }

        // 不能修改团长自己的角色
        if ("owner".equals(member.getRole())) {
            throw new ServiceException(GlobalErrorCodeConstants.CANNOT_REMOVE_OWNER);
        }

        member.setRole(targetRole);
        memberMapper.updateById(member);
        log.info("设置成员角色成功: memberId={}, role={}, operator={}", memberId, targetRole, operatorUserId);
        return member;
    }

    /**
     * 删除成员
     * 权限：团长或管理员可操作
     * 规则：
     * - 不能删除团长
     * - 不能删除自己（团长通过转移团长退出，管理员通过退出军团退出）
     * - 管理员不能删除其他管理员（团长可以删除管理员）
     *
     * @param id             成员ID
     * @param operatorUserId 操作人用户ID
     */
    public void deleteMember(Long id, String operatorUserId) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_NOT_FOUND);
        }

        // 不能删除团长
        if ("owner".equals(member.getRole())) {
            throw new ServiceException(GlobalErrorCodeConstants.CANNOT_REMOVE_OWNER);
        }

        // 校验军团存在
        Legion legion = legionMapper.selectById(member.getLegionId());
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        // 判断操作人角色
        boolean isOwner = operatorUserId != null && operatorUserId.equals(legion.getOwnerUserId());
        boolean isAdmin = false;
        if (!isOwner && operatorUserId != null) {
            Member operator = memberMapper.selectOne(
                    new LambdaQueryWrapper<Member>()
                            .eq(Member::getLegionId, member.getLegionId())
                            .eq(Member::getUserId, operatorUserId));
            isAdmin = operator != null && "admin".equals(operator.getRole());
        }

        // 仅团长或管理员可删除
        if (!isOwner && !isAdmin) {
            throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
        }

        // 不能删除自己
        if (operatorUserId != null && operatorUserId.equals(member.getUserId())) {
            throw new ServiceException(GlobalErrorCodeConstants.CANNOT_REMOVE_SELF);
        }

        // 管理员不能删除其他管理员（团长可以）
        if (!isOwner && isAdmin && "admin".equals(member.getRole())) {
            throw new ServiceException(GlobalErrorCodeConstants.ADMIN_CANNOT_REMOVE_ADMIN);
        }

        memberMapper.deleteById(id);
        log.info("删除成员成功: id={}, operator={}", id, operatorUserId);
    }
}