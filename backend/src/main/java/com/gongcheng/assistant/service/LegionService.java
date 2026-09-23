package com.gongcheng.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gongcheng.assistant.common.GlobalErrorCodeConstants;
import com.gongcheng.assistant.common.SecurityValidator;
import com.gongcheng.assistant.common.ServiceException;
import com.gongcheng.assistant.dto.CreateLegionRequest;
import com.gongcheng.assistant.dto.JoinLegionRequest;
import com.gongcheng.assistant.dto.JoinLegionResponse;
import com.gongcheng.assistant.entity.Legion;
import com.gongcheng.assistant.entity.Member;
import com.gongcheng.assistant.mapper.LegionMapper;
import com.gongcheng.assistant.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegionService {

    private final LegionMapper legionMapper;
    private final MemberMapper memberMapper;
    private final ApiQueryService apiQueryService;

    @Transactional
    public Legion createLegion(CreateLegionRequest request) {
        // 安全校验
        SecurityValidator.validateUserId(request.getUserId());
        SecurityValidator.validateLegionName(request.getName());

        String code = SecurityValidator.sanitize(request.getCode());
        String name = SecurityValidator.sanitize(request.getName());

        // 口令唯一校验
        Long codeCount = legionMapper.selectCount(
                new LambdaQueryWrapper<Legion>().eq(Legion::getCode, code));
        if (codeCount != null && codeCount > 0) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_CODE_EXISTS);
        }

        // 名称唯一校验
        Long nameCount = legionMapper.selectCount(
                new LambdaQueryWrapper<Legion>().eq(Legion::getName, name));
        if (nameCount != null && nameCount > 0) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NAME_EXISTS);
        }

        Legion legion = Legion.builder()
                .name(name)
                .code(code)
                .ownerUserId(request.getUserId())
                .build();
        legionMapper.insert(legion);
        log.info("创建军团成功: id={}, name={}, owner={}", legion.getId(), legion.getName(), request.getUserId());
        return legion;
    }

    @Transactional
    public JoinLegionResponse joinLegion(JoinLegionRequest request) {
        // 安全校验
        SecurityValidator.validateUserId(request.getUserId());

        String trimmedCode = SecurityValidator.sanitize(request.getCode());
        Legion legion = legionMapper.selectOne(
                new LambdaQueryWrapper<Legion>().eq(Legion::getCode, trimmedCode));
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_FOUND);
        }

        Member createdMember = null;

        // 如果携带了接口链接，加入时自动创建成员
        if (request.getApiUrl() != null && !request.getApiUrl().trim().isEmpty()) {
            String apiUrl = request.getApiUrl().trim();

            // 检查是否已在军团中（通过 userId 或 apiUrl）
            Long existCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<Member>()
                            .eq(Member::getLegionId, legion.getId())
                            .and(w -> w.eq(Member::getUserId, request.getUserId())
                                    .or()
                                    .eq(Member::getApiUrl, apiUrl)));
            if (existCount != null && existCount > 0) {
                throw new ServiceException(GlobalErrorCodeConstants.MEMBER_ALREADY_IN_LEGION);
            }

            Member member = new Member();
            member.setLegionId(legion.getId());
            member.setUserId(request.getUserId());
            member.setRole("member");
            apiQueryService.fillMemberFromApi(member, apiUrl);
            memberMapper.insert(member);
            createdMember = member;
            log.info("加入军团并自动创建成员: legionId={}, memberId={}, roleName={}",
                    legion.getId(), member.getId(), member.getRoleName());
        }

        log.info("加入军团成功: id={}, name={}, userId={}", legion.getId(), legion.getName(), request.getUserId());
        return JoinLegionResponse.builder()
                .legion(legion)
                .member(createdMember)
                .build();
    }

    public Legion getLegion(Long id) {
        Legion legion = legionMapper.selectById(id);
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }
        return legion;
    }

    /**
     * 根据用户ID查询所在军团（先查是否为团长，再查是否为成员）
     * @return 军团信息，未加入任何军团返回 null
     */
    public Legion getMyLegion(String userId) {
        // 1. 是否为某个军团的团长
        Legion ownerLegion = legionMapper.selectOne(
                new LambdaQueryWrapper<Legion>().eq(Legion::getOwnerUserId, userId));
        if (ownerLegion != null) {
            return ownerLegion;
        }

        // 2. 是否为某个军团的成员
        Member myMember = memberMapper.selectOne(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getUserId, userId)
                        .last("LIMIT 1"));
        if (myMember != null) {
            return legionMapper.selectById(myMember.getLegionId());
        }

        return null;
    }

    /**
     * 转移团长（仅当前团长可操作）
     */
    @Transactional
    public Legion transferOwner(Long legionId, String operatorUserId, Long targetMemberId) {
        Legion legion = legionMapper.selectById(legionId);
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        // 校验操作人是团长
        if (!operatorUserId.equals(legion.getOwnerUserId())) {
            throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
        }

        // 校验目标成员存在且属于该军团
        Member target = memberMapper.selectById(targetMemberId);
        if (target == null || !target.getLegionId().equals(legionId)) {
            throw new ServiceException(GlobalErrorCodeConstants.TARGET_NOT_IN_LEGION);
        }

        // 不能转移给自己
        if (target.getUserId() != null && target.getUserId().equals(operatorUserId)) {
            throw new ServiceException(GlobalErrorCodeConstants.CANNOT_TRANSFER_TO_SELF);
        }

        // 更新团长
        String newOwnerUserId = target.getUserId();
        legion.setOwnerUserId(newOwnerUserId);
        legionMapper.updateById(legion);

        // 更新目标成员角色为 owner
        target.setRole("owner");
        memberMapper.updateById(target);

        // 原团长如果在成员列表中，角色降为 member
        Member oldOwnerMember = memberMapper.selectOne(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, legionId)
                        .eq(Member::getUserId, operatorUserId));
        if (oldOwnerMember != null && !oldOwnerMember.getId().equals(targetMemberId)) {
            oldOwnerMember.setRole("member");
            memberMapper.updateById(oldOwnerMember);
        }

        log.info("转移团长成功: legionId={}, from={}, to={}", legionId, operatorUserId, newOwnerUserId);
        return legion;
    }

    /**
     * 退出军团（删除当前用户的成员记录；团长需先转移或军团只剩自己时可解散）
     */
    @Transactional
    public void exitLegion(Long legionId, String userId) {
        Legion legion = legionMapper.selectById(legionId);
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        boolean isOwner = userId.equals(legion.getOwnerUserId());

        // 查询该用户在军团中的成员记录
        Member myMember = memberMapper.selectOne(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, legionId)
                        .eq(Member::getUserId, userId));

        if (isOwner) {
            // 统计军团总成员数
            Long memberCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<Member>().eq(Member::getLegionId, legionId));

            // 如果还有其他成员，团长必须先转移
            if (memberCount != null && memberCount > 1) {
                throw new ServiceException(GlobalErrorCodeConstants.OWNER_MUST_TRANSFER_FIRST);
            }

            // 军团只剩团长自己（或没有成员记录），解散军团
            // 先删除所有成员（理论上只剩自己）
            memberMapper.delete(
                    new LambdaQueryWrapper<Member>().eq(Member::getLegionId, legionId));
            // 删除军团
            legionMapper.deleteById(legionId);
            log.info("团长退出并解散军团: legionId={}, userId={}", legionId, userId);
        } else {
            // 普通成员退出：删除自己的成员记录
            if (myMember != null) {
                memberMapper.deleteById(myMember.getId());
                log.info("成员退出军团: legionId={}, memberId={}, userId={}",
                        legionId, myMember.getId(), userId);
            }
        }
    }
}
