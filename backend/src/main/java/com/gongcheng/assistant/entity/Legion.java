package com.gongcheng.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("legion")
@Schema(description = "军团")
public class Legion {

    @TableId(type = IdType.AUTO)
    @Schema(description = "军团ID")
    private Long id;

    @TableField("name")
    @Schema(description = "军团名称")
    private String name;

    @TableField("code")
    @Schema(description = "军团口令")
    private String code;

    @TableField("owner_user_id")
    @Schema(description = "创建人（军团所有人）用户ID")
    private String ownerUserId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
