package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 关注用户
     * @param id
     * @return
     */
    @Override
    public Result followById(Long id,Boolean isFollow) {
        Long myUserId = UserHolder.getUser().getId();
        String key = "follows:" + myUserId;

        if (isFollow){
            Follow follow = new Follow();
            follow.setUserId(myUserId);
            follow.setFollowUserId(id);
            followMapper.insert(follow);

            stringRedisTemplate.opsForSet().add(key,id.toString());

            return Result.ok();
        }else{
            Map<String,Object> hashMap = new HashMap();
            hashMap.put("user_id",UserHolder.getUser().getId().toString());
            hashMap.put("follow_user_id",id.toString());
            followMapper.deleteByMap(hashMap);
            stringRedisTemplate.opsForSet().remove(key,id.toString());
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
    public Result common(Long id) {
        Long myId = UserHolder.getUser().getId();

        String key1 = "follows:" + myId;
        String key2 = "follows:" + id;

        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);

        if (intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> userList = new ArrayList<>();
        for (String str:intersect){
            QueryWrapper queryWrapper = new QueryWrapper();
            queryWrapper.eq("id",str);
            User user = userMapper.selectOne(queryWrapper);
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user,userDTO);
            userList.add(userDTO);
        }

        return Result.ok(userList);
    }
}
