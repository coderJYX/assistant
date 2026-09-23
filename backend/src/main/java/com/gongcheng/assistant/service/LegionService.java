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

/**
 * 军团服务
 * 负责军团的创建、加入、查询、转移团长、退出、修改口令等业务逻辑
 * 角色说明：owner=团长（军团创建人），admin=管理员，member=普通成员
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegionService {

    private final LegionMapper legionMapper;
    private final MemberMapper memberMapper;
    private final ApiQueryService apiQueryService;

    /**
     * 创建军团
     * 1. 校验用户ID和军团名称格式
     * 2. 校验口令和名称全局唯一
     * 3. 创建军团，创建人为团长
     *
     * @param request 创建军团请求（名称、口令、用户ID）
     * @return 新创建的军团信息
     */
    @Transactional
    public Legion createLegion(CreateLegionRequest request) {
        // 安全校验：用户ID和军团名称格式
        SecurityValidator.validateUserId(request.getUserId());
        SecurityValidator.validateLegionName(request.getName());

        // 输入清洗
        String code = SecurityValidator.sanitize(request.getCode());
        String name = SecurityValidator.sanitize(request.getName());

        // 校验口令全局唯一（幂等性）
        Long codeCount = legionMapper.selectCount(
                new LambdaQueryWrapper<Legion>().eq(Legion::getCode, code));
        if (codeCount != null && codeCount > 0) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_CODE_EXISTS);
        }

        // 校验军团名称全局唯一（幂等性）
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

    /**
     * 加入军团
     * 1. 通过口令查找军团
     * 2. 若携带梦游社链接，自动创建成员记录
     * 3. 校验用户和链接在该军团下不重复
     *
     * @param request 加入军团请求（口令、用户ID、可选梦游社链接）
     * @return 军团信息和自动创建的成员信息
     */
    @Transactional
    public JoinLegionResponse joinLegion(JoinLegionRequest request) {
        // 安全校验：用户ID格式
        SecurityValidator.validateUserId(request.getUserId());

        // 输入清洗并通过口令查找军团
        String trimmedCode = SecurityValidator.sanitize(request.getCode());
        Legion legion = legionMapper.selectOne(
                new LambdaQueryWrapper<Legion>().eq(Legion::getCode, trimmedCode));
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_FOUND);
        }

        Member createdMember = null;

        // 携带梦游社链接时，自动创建成员记录
        if (request.getApiUrl() != null && !request.getApiUrl().trim().isEmpty()) {
            String apiUrl = request.getApiUrl().trim();

            // 校验同一军团下用户或链接不重复（幂等性）
            Long existCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<Member>()
                            .eq(Member::getLegionId, legion.getId())
                            .and(w -> w.eq(Member::getUserId, request.getUserId())
                                    .or()
                                    .eq(Member::getApiUrl, apiUrl)));
            if (existCount != null && existCount > 0) {
                throw new ServiceException(GlobalErrorCodeConstants.MEMBER_ALREADY_IN_LEGION);
            }

            // 创建成员并拉取游戏数据
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

    /**
     * 根据ID查询军团信息
     *
     * @param id 军团ID
     * @return 军团信息
     */
    public Legion getLegion(Long id) {
        Legion legion = legionMapper.selectById(id);
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }
        return legion;
    }

    /**
     * 查询用户所在的军团
     * 先查是否为某军团团长，再查是否为某军团成员
     *
     * @param userId 用户ID
     * @return 所在军团，未加入返回null
     */
    public Legion getMyLegion(String userId) {
        // 1. 查是否为某军团团长
        Legion ownerLegion = legionMapper.selectOne(
                new LambdaQueryWrapper<Legion>().eq(Legion::getOwnerUserId, userId));
        if (ownerLegion != null) {
            return ownerLegion;
        }

        // 2. 查是否为某军团成员
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
     * 转移团长
     * 1. 仅当前团长可操作
     * 2. 目标必须是本军团成员
     * 3. 不能转移给自己
     * 4. 转移后新团长角色设为owner，原团长变为普通成员
     *
     * @param legionId       军团ID
     * @param operatorUserId 当前团长用户ID
     * @param targetMemberId 目标成员ID
     * @return 更新后的军团信息
     */
    @Transactional
    public Legion transferOwner(Long legionId, String operatorUserId, Long targetMemberId) {
        // 校验军团存在
        Legion legion = legionMapper.selectById(legionId);
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        // 校验操作人是团长
        if (!operatorUserId.equals(legion.getOwnerUserId())) {
            throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
        }

        // 校验目标是本军团成员
        Member target = memberMapper.selectById(targetMemberId);
        if (target == null || !target.getLegionId().equals(legionId)) {
            throw new ServiceException(GlobalErrorCodeConstants.TARGET_NOT_IN_LEGION);
        }

        // 不能转移给自己
        if (target.getUserId() != null && target.getUserId().equals(operatorUserId)) {
            throw new ServiceException(GlobalErrorCodeConstants.CANNOT_TRANSFER_TO_SELF);
        }

        // 更新军团团长
        String newOwnerUserId = target.getUserId();
        legion.setOwnerUserId(newOwnerUserId);
        legionMapper.updateById(legion);

        // 新团长角色设为owner
        target.setRole("owner");
        memberMapper.updateById(target);

        // 原团长变为普通成员（如果原团长有成员记录）
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
     * 退出军团
     * - 普通成员：删除自己的成员记录
     * - 团长：军团只剩自己时解散军团（删除所有成员和军团），否则需先转移团长
     *
     * @param legionId 军团ID
     * @param userId   退出用户ID
     */
    @Transactional
    public void exitLegion(Long legionId, String userId) {
        // 校验军团存在
        Legion legion = legionMapper.selectById(legionId);
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        boolean isOwner = userId.equals(legion.getOwnerUserId());

        // 查询用户的成员记录
        Member myMember = memberMapper.selectOne(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getLegionId, legionId)
                        .eq(Member::getUserId, userId));

        if (isOwner) {
            // 团长退出：检查军团成员数量
            Long memberCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<Member>().eq(Member::getLegionId, legionId));

            // 军团还有其他成员时，团长需先转移团长
            if (memberCount != null && memberCount > 1) {
                throw new ServiceException(GlobalErrorCodeConstants.OWNER_MUST_TRANSFER_FIRST);
            }

            // 只剩团长自己，解散军团
            memberMapper.delete(
                    new LambdaQueryWrapper<Member>().eq(Member::getLegionId, legionId));
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

    /**
     * 修改军团口令
     * 1. 仅团长可操作
     * 2. 新口令长度4-32位，仅限中英文数字
     * 3. 新口令全局唯一
     *
     * @param legionId       军团ID
     * @param operatorUserId 操作人用户ID（必须是团长）
     * @param newCode        新口令
     * @return 更新后的军团信息
     */
    @Transactional
    public Legion updateCode(Long legionId, String operatorUserId, String newCode) {
        // 安全校验：用户ID格式
        SecurityValidator.validateUserId(operatorUserId);
        String trimmedCode = SecurityValidator.sanitize(newCode);

        // 校验口令格式
        if (trimmedCode == null || trimmedCode.length() < 4 || trimmedCode.length() > 32) {
            throw new ServiceException(GlobalErrorCodeConstants.PARAM_ERROR.getCode(), "口令长度需4-32位");
        }
        if (!trimmedCode.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9]+$")) {
            throw new ServiceException(GlobalErrorCodeConstants.PARAM_ERROR.getCode(), "口令仅支持中文、英文、数字组合");
        }

        // 校验军团存在
        Legion legion = legionMapper.selectById(legionId);
        if (legion == null) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_NOT_EXIST);
        }

        // 校验操作人是团长
        if (!operatorUserId.equals(legion.getOwnerUserId())) {
            throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION);
        }

        // 新口令与原口令相同，直接返回
        if (trimmedCode.equals(legion.getCode())) {
            return legion;
        }

        // 校验新口令全局唯一
        Long count = legionMapper.selectCount(
                new LambdaQueryWrapper<Legion>().eq(Legion::getCode, trimmedCode));
        if (count != null && count > 0) {
            throw new ServiceException(GlobalErrorCodeConstants.LEGION_CODE_EXISTS);
        }

        legion.setCode(trimmedCode);
        legionMapper.updateById(legion);
        log.info("修改军团口令成功: legionId={}, newCode={}", legionId, trimmedCode);
        return legion;
    }
}