package com.gongcheng.assistant.common;

/**
 * 全局错误码枚举
 * 参考 HTTP 状态码分段
 */
public interface GlobalErrorCodeConstants {

    ErrorCode SUCCESS = new ErrorCode(200, "成功");

    // ========== 客户端错误段 400-499 ==========
    ErrorCode BAD_REQUEST = new ErrorCode(400, "请求参数不正确");
    ErrorCode PARAM_ERROR = new ErrorCode(400, "参数校验失败");
    ErrorCode UNAUTHORIZED = new ErrorCode(401, "未登录");
    ErrorCode FORBIDDEN = new ErrorCode(403, "无操作权限");
    ErrorCode NOT_FOUND = new ErrorCode(404, "请求未找到");
    ErrorCode METHOD_NOT_ALLOWED = new ErrorCode(405, "请求方法不正确");

    // ========== 服务端错误段 500-599 ==========
    ErrorCode INTERNAL_SERVER_ERROR = new ErrorCode(500, "系统异常");

    // ========== 业务错误段 1000-9999 ==========
    ErrorCode LEGION_CODE_EXISTS = new ErrorCode(1001, "该口令已被使用，请更换口令");
    ErrorCode LEGION_NAME_EXISTS = new ErrorCode(1005, "该军团名称已存在，请更换名称");
    ErrorCode LEGION_NOT_FOUND = new ErrorCode(1002, "未找到该口令对应的军团，请确认口令是否正确");
    ErrorCode LEGION_NOT_EXIST = new ErrorCode(1003, "军团不存在，请先创建或加入军团");
    ErrorCode MEMBER_API_URL_EXISTS = new ErrorCode(1101, "该接口链接已在军团中存在");
    ErrorCode MEMBER_NOT_FOUND = new ErrorCode(1102, "成员不存在");
    ErrorCode MEMBER_ALREADY_IN_LEGION = new ErrorCode(1103, "您已在该军团中");
    ErrorCode API_QUERY_FAILED = new ErrorCode(1201, "接口查询失败");
    ErrorCode NO_PERMISSION = new ErrorCode(1301, "无操作权限，仅军团所有人或管理员可执行此操作");
    ErrorCode ONLY_OWNER_CAN_SET_ADMIN = new ErrorCode(1302, "仅军团所有人可设置管理员");
    ErrorCode CANNOT_REMOVE_OWNER = new ErrorCode(1303, "不能移除军团所有人");
    ErrorCode OWNER_MUST_TRANSFER_FIRST = new ErrorCode(1304, "军团所有人需先转移团长后才能退出");
    ErrorCode TARGET_NOT_IN_LEGION = new ErrorCode(1305, "目标成员不在该军团中");
    ErrorCode CANNOT_TRANSFER_TO_SELF = new ErrorCode(1306, "不能将团长转移给自己");
    ErrorCode ADMIN_CANNOT_REMOVE_ADMIN = new ErrorCode(1307, "管理员不能删除其他管理员");
    ErrorCode CANNOT_REMOVE_SELF = new ErrorCode(1308, "不能删除自己，请使用退出军团功能");
}
