package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private FollowMapper followMapper;

    /**
     * 关注用户
     * @param id
     * @return
     */
    @Override
    public Result followById(Long id,Boolean isFollow) {
        Long myUserId = UserHolder.getUser().getId();
        if (isFollow){
            Follow follow = new Follow();
            follow.setUserId(myUserId);
            follow.setFollowUserId(id);
            followMapper.insert(follow);
            return Result.ok();
        }else{
            Map<String,Object> hashMap = new HashMap();
            hashMap.put("user_id",UserHolder.getUser().getId().toString());
            hashMap.put("follow_user_id",id.toString());
            followMapper.deleteByMap(hashMap);
            return Result.ok();
        }
    }


    /**
     * 查看是否关注了
     * @param id
     * @return
     */
    @Override
    public Result followOrNotById(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.ok();
        }

        Long myUserId = UserHolder.getUser().getId();
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("user_id",myUserId);
        queryWrapper.eq("follow_user_id",id);

        Follow follow = followMapper.selectOne(queryWrapper);
        if (follow == null){
            return Result.ok(false);
        }

        return Result.ok(true);
    }

    /**
     * 共同关注
     * @return
     */
    @Override
    public Result common() {
        return null;
    }
}
