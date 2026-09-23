package com.gongcheng.assistant.config;

import com.gongcheng.assistant.common.GlobalErrorCodeConstants;
import com.gongcheng.assistant.common.ServiceException;
import com.gongcheng.assistant.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 将异常翻译成 ApiResponse + 对应错误码
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常 ServiceException
     */
    @ExceptionHandler(ServiceException.class)
    public ApiResponse<Void> serviceExceptionHandler(ServiceException ex) {
        log.info("[业务异常] code={}, msg={}", ex.getCode(), ex.getMessage());
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理请求参数缺失
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> missingParamHandler(MissingServletRequestParameterException ex) {
        log.warn("[请求参数缺失] {}", ex.getParameterName());
        return ApiResponse.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                "请求参数缺失: " + ex.getParameterName());
    }

    /**
     * 处理请求参数类型错误
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> typeMismatchHandler(MethodArgumentTypeMismatchException ex) {
        log.warn("[参数类型错误] {}", ex.getName());
        return ApiResponse.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(),
                "请求参数类型错误: " + ex.getName());
    }

    /**
     * 处理 @RequestBody 参数校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> validExceptionHandler(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {}", msg);
        return ApiResponse.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), msg);
    }

    /**
     * 处理表单参数绑定失败
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> bindExceptionHandler(BindException ex) {
        FieldError fieldError = ex.getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数绑定失败";
        log.warn("[参数绑定失败] {}", msg);
        return ApiResponse.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), msg);
    }

    /**
     * 处理约束校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> constraintViolationHandler(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[约束校验失败] {}", msg);
        return ApiResponse.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), msg);
    }

    /**
     * 处理请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> methodNotSupportedHandler(HttpRequestMethodNotSupportedException ex) {
        log.warn("[请求方法不支持] {}", ex.getMethod());
        return ApiResponse.error(GlobalErrorCodeConstants.METHOD_NOT_ALLOWED.getCode(),
                "请求方法不正确: " + ex.getMethod());
    }

    /**
     * 兜底处理所有异常
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> defaultExceptionHandler(Exception ex) {
        log.error("[系统异常]", ex);
        return ApiResponse.error(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                "系统内部错误，请稍后重试");
    }
}
