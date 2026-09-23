package com.gongcheng.assistant.common;

import java.util.regex.Pattern;

/**
 * 安全校验工具类
 * 防止脏数据、垃圾数据注入
 */
public class SecurityValidator {

    /**
     * 微信 openid 格式：
     * - 以小写字母 o 开头
     * - 由数字、大小写字母、下划线 _、短横线 - 组成
     * - 长度 20-64 字符（实际约 28，留余量）
     */
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^o[a-zA-Z0-9_-]{19,63}$");

    /** 军团名称：中文、英文、数字、空格，2-50位 */
    private static final Pattern LEGION_NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s]{2,50}$");

    /**
     * 校验 userId 格式
     */
    public static void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION.getCode(), "用户ID不能为空");
        }
        String trimmed = userId.trim();
        if (!USER_ID_PATTERN.matcher(trimmed).matches()) {
            throw new ServiceException(GlobalErrorCodeConstants.NO_PERMISSION.getCode(), "用户ID格式不合法");
        }
    }

    /**
     * 校验军团名称
     */
    public static void validateLegionName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ServiceException(GlobalErrorCodeConstants.PARAM_ERROR.getCode(), "军团名称不能为空");
        }
        String trimmed = name.trim();
        if (!LEGION_NAME_PATTERN.matcher(trimmed).matches()) {
            throw new ServiceException(GlobalErrorCodeConstants.PARAM_ERROR.getCode(),
                    "军团名称仅支持中文、英文、数字，长度2-50位");
        }
    }

    /**
     * 清理输入字符串（去除首尾空格、控制字符）
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        // 去除首尾空格和控制字符
        return input.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
    }
}
