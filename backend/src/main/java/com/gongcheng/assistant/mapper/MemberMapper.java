package com.gongcheng.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gongcheng.assistant.entity.Member;
import org.apache.ibatis.annotations.Mapper;

/**
 * 军团成员数据访问层
 * 继承 MyBatis Plus BaseMapper，提供基础CRUD操作
 */
@Mapper
public interface MemberMapper extends BaseMapper<Member> {
}