package com.gongcheng.assistant.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 错误码对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorCode implements Serializable {

    /**
     * 错误码
     */
    private Integer code;

    /**
     * 错误提示
     */
    private String msg;
}
