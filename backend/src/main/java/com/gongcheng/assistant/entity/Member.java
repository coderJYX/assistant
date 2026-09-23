package com.gongcheng.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 军团成员实体类
 * 对应数据库 member 表，存储成员绑定的游戏角色信息
 * 角色说明：role 字段 owner=团长, admin=管理员, member=普通成员
 * 特殊宝石：specialGems 字段用逗号分隔存储筛选出的特殊宝石效果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("member")
@Schema(description = "军团成员")
public class Member {

    @TableId(type = IdType.AUTO)
    @Schema(description = "成员ID")
    private Long id;

    @TableField("legion_id")
    @Schema(description = "所属军团ID")
    private Long legionId;

    @TableField("user_id")
    @Schema(description = "对应用户ID")
    private String userId;

    @TableField("role")
    @Schema(description = "角色：owner/admin/member")
    private String role;

    @TableField("api_url")
    @Schema(description = "梦游社链接（用户原始输入）")
    private String apiUrl;

    @TableField("server_id")
    @Schema(description = "游戏服务器ID")
    private String serverId;

    @TableField("game_user_id")
    @Schema(description = "游戏用户ID")
    private String gameUserId;

    @TableField("game_role_id")
    @Schema(description = "游戏角色ID")
    private String gameRoleId;

    @TableField("role_name")
    @Schema(description = "角色名称")
    private String roleName;

    @TableField("progress")
    @Schema(description = "进度")
    private String progress;

    @TableField("atk")
    @Schema(description = "攻击力")
    private Integer atk;

    @TableField("gun_dmg_bonus")
    @Schema(description = "枪械伤害加成")
    private String gunDmgBonus;

    @TableField("crit_dmg_bonus")
    @Schema(description = "暴击伤害加成")
    private String critDmgBonus;

    @TableField("special_gems")
    @Schema(description = "特殊宝石效果（逗号分隔）")
    private String specialGems;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
