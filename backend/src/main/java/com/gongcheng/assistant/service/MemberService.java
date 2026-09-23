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

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final LegionMapper legionMapper;
    private final ApiQueryService apiQueryService;

    public Member addMember(AddMemberRequest request) {
        // 安全校验
        SecurityValidator.validateUserId(request.getUserId());

        // 校验军团存在
        Legion legion = legionMapper.selectById(request.getLegionId());
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        String apiUrl = SecurityValidator.sanitize(request.getApiUrl());

        // 先解析链接获取游戏参数（用于校验重复和存储）
        ApiQueryService.GameParams params = apiQueryService.parseGameParams(apiUrl);

        // 校验同一军团下角色不重复（用 gameRoleId 判断）
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, legion.getId())
                        .eq(Member::getGameRoleId, params.roleId()));
        if (count != null && count > 0) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_API_URL_EXISTS);
        }

        Member member = new Member();
        member.setLegionId(legion.getId());
        member.setUserId(request.getUserId());

        // 军团所有人绑定角色时默认为 owner，其他为 member
        if (request.getUserId() != null && request.getUserId().equals(legion.getOwnerUserId())) {
            member.setRole("owner");
        } else {
            member.setRole("member");
        }

        // 调用接口拉取数据（内部存储参数，不存完整链接）
        apiQueryService.fillMemberFromApi(member, apiUrl);

        memberMapper.insert(member);
        log.info("添加成员成功: id={}, roleName={}, legionId={}",
                member.getId(), member.getRoleName(), member.getLegionId());
        return member;
    }

    public List<Member> listMembers(Long legionId) {
        Legion legion = legionMapper.selectById(legionId);
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }
        List<Member> members = memberMapper.selectList(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, legionId));

        // 按 progress 中的数字倒序排序（progress 格式如 "229.大竞技场"）
        members.sort((a, b) -> {
            int pa = extractProgressNumber(a.getProgress());
            int pb = extractProgressNumber(b.getProgress());
            return Integer.compare(pb, pa);
        });

        // 设置展示用链接
        members.forEach(this::setDisplayUrl);

        return members;
    }

    /**
     * 从 progress 字符串中提取数字，如 "229.大竞技场" -> 229
     */
    private int extractProgressNumber(String progress) {
        if (progress == null || progress.isEmpty()) return 0;
        StringBuilder sb = new StringBuilder();
        for (char c : progress.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            } else if (sb.length() > 0) {
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
     * 查询用户在指定军团中的成员记录（用于判断是否已绑定角色）
     * @return 成员记录，未绑定返回 null
     */
    public Member getMyMember(Long legionId, String userId) {
        return memberMapper.selectOne(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, legionId)
                        .eq(Member::getUserId, userId)
                        .last("LIMIT 1"));
    }

    public Member getMember(Long id) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_NOT_FOUND);
        }
        setDisplayUrl(member);
        return member;
    }

    /**
     * 设置展示用完整链接（H5格式，不存数据库，仅返回给前端展示）
     */
    private void setDisplayUrl(Member member) {
        if (member.getServerId() != null && member.getGameUserId() != null && member.getGameRoleId() != null) {
            member.setDisplayUrl(String.format(
                    "https://h5.docoi.cc/t5Config/homePage/%s?userId=%s&roleId=%s",
                    member.getServerId(), member.getGameUserId(), member.getGameRoleId()));
        } else if (member.getApiUrl() != null) {
            // 兼容旧数据
            member.setDisplayUrl(member.getApiUrl());
        }
    }

    public Member updateMember(Long id, String apiUrl, String operatorUserId) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_NOT_FOUND);
        }

        // 权限校验：本人、团长或管理员才能修改
        checkSelfOrPrivileged(member, operatorUserId);

        String trimmedUrl = SecurityValidator.sanitize(apiUrl);

        // 解析新链接的游戏参数
        ApiQueryService.GameParams newParams = apiQueryService.parseGameParams(trimmedUrl);

        // 校验同一军团下角色不重复（排除自己）
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, member.getLegionId())
                        .eq(Member::getGameRoleId, newParams.roleId())
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

    public Member refreshMember(Long id, String operatorUserId) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_NOT_FOUND);
        }

        // 权限校验：本人、团长或管理员才能刷新
        checkSelfOrPrivileged(member, operatorUserId);

        // 用已存储的游戏参数重新拉取数据（不需要完整链接）
        apiQueryService.refreshMemberFromParams(member);

        memberMapper.updateById(member);
        log.info("刷新成员成功: id={}, roleName={}", member.getId(), member.getRoleName());
        return member;
    }

    /**
     * 权限校验：操作人必须是本人、团长或军团管理员
     */
    private void checkSelfOrPrivileged(Member target, String operatorUserId) {
        if (operatorUserId == null) {
            throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
        }
        // 本人操作放行
        if (operatorUserId.equals(target.getUserId())) {
            return;
        }
        // 查军团，判断是否团长
        Legion legion = legionMapper.selectById(target.getLegionId());
        if (legion != null && operatorUserId.equals(legion.getOwnerUserId())) {
            return;
        }
        // 查自己的成员记录，判断是否管理员
        Member operator = memberMapper.selectOne(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, target.getLegionId())
                        .eq(Member::getUserId, operatorUserId));
        if (operator != null && "admin".equals(operator.getRole())) {
            return;
        }
        throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
    }

    /**
     * 一键同步军团所有成员数据（仅团长或管理员）
     */
    public int syncAll(Long legionId, String operatorUserId) {
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
     * 设置成员角色（仅军团所有人可操作）
     */
    public Member setRole(Long memberId, String operatorUserId, String targetRole) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_NOT_FOUND);
        }

        // 校验操作人是军团所有人
        Legion legion = legionMapper.selectById(member.getLegionId());
        if (legion == null || !operatorUserId.equals(legion.getOwnerUserId())) {
            throw new ServiceException(GlobalErrorCodeConstants.ONLY_OWNER_CAN_SET_ADMIN);
        }

        // 不能修改所有人自己的角色
        if ("owner".equals(member.getRole())) {
            throw new ServiceException(GlobalErrorCodeConstants.CANNOT_REMOVE_OWNER);
        }

        member.setRole(targetRole);
        memberMapper.updateById(member);
        log.info("设置成员角色成功: memberId={}, role={}, operator={}", memberId, targetRole, operatorUserId);
        return member;
    }

    /**
     * 删除成员（军团所有人或管理员可操作）
     */
    public void deleteMember(Long id, String operatorUserId) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw new ServiceException(GlobalErrorCodeConstants.MEMBER_NOT_FOUND);
        }

        // 不能删除军团所有人
        if ("owner".equals(member.getRole())) {
            throw new ServiceException(GlobalErrorCodeConstants.CANNOT_REMOVE_OWNER);
        }

        // 校验操作人权限：军团所有人或管理员
        Legion legion = legionMapper.selectById(member.getLegionId());
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        boolean isOwner = operatorUserId != null && operatorUserId.equals(legion.getOwnerUserId());
        boolean isAdmin = false;
        if (!isOwner && operatorUserId != null) {
            Member operator = memberMapper.selectOne(
                    new LambdaQueryWrapper<Member>()
                            .eq(Member::getLegionId, member.getLegionId())
                            .eq(Member::getUserId, operatorUserId));
            isAdmin = operator != null && "admin".equals(operator.getRole());
        }

        if (!isOwner && !isAdmin) {
            throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
        }

        // 不能删除自己（团长通过转移团长退出，管理员通过退出军团退出）
        if (operatorUserId != null && operatorUserId.equals(member.getUserId())) {
            throw new ServiceException(GlobalErrorCodeConstants.CANNOT_REMOVE_SELF);
        }

        // 管理员不能删除其他管理员（团长可以删除管理员）
        if (!isOwner && isAdmin && "admin".equals(member.getRole())) {
            throw new ServiceException(GlobalErrorCodeConstants.ADMIN_CANNOT_REMOVE_ADMIN);
        }

        memberMapper.deleteById(id);
        log.info("删除成员成功: id={}, operator={}", id, operatorUserId);
    }
}
