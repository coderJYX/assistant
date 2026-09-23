package com.gongcheng.assistant.dto;

import com.gongcheng.assistant.entity.Legion;
import com.gongcheng.assistant.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "加入军团响应")
public class JoinLegionResponse {

    @Schema(description = "军团信息")
    private Legion legion;

    @Schema(description = "自动创建的成员信息（邀请加入携带链接时返回）")
    private Member member;
}
