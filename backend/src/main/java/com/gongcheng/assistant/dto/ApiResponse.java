package com.gongcheng.assistant.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果封装
 * 所有接口返回统一格式：{code, message, data}
 *
 * @param <T> 数据类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {

    /**
     * 响应状态码，200表示成功
     */
    private int code;

    /**
     * 响应提示信息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 成功响应（默认提示ok）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "ok", data);
    }

    /**
     * 成功响应（自定义提示）
     *
     * @param message 提示信息
     * @param data    响应数据
     * @param <T>     数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    /**
     * 失败响应
     *
     * @param code    错误码
     * @param message 错误提示
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}