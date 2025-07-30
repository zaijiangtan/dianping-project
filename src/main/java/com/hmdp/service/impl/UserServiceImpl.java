package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpServletRequest request) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.无效的手机号
            return Result.fail("无效的手机号");
        }

        //3.手机号有效,检查冷却器
        String cooldownKey = LOGIN_COOLDOWN_KEY + phone;
        String cooldown = stringRedisTemplate.opsForValue().get(cooldownKey);
        if (cooldown != null) {
            // 前端倒计时并没有做适配
            return Result.fail("登陆过于频繁，请稍后再试。");
        }

        //4.限制同一个ip一分钟内请求次数（5次）
        String ip = request.getRemoteAddr();
        String ipKey = LOGIN_IP_KEY + ip;

        // 每次请求 +1
        Long count = stringRedisTemplate.opsForValue().increment(ipKey);

        // 设置过期时间（只在首次设置时设置）
        if (count == 1) {
            stringRedisTemplate.expire(ipKey, LOGIN_IP_TTL, TimeUnit.SECONDS);
        }

        // 超过阈值限制（如每分钟最多 5 次）
        if (count > 5) {
            return Result.fail("请求过于频繁，请稍后再试");
        }

        //5.生成验证码
        String code = RandomUtil.randomNumbers(6);

        //6.保存验证码到Redis(验证码2分钟后过期)
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code,
                LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //7.设置冷却时间(60s)
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", LOGIN_COOLDOWN_TTL, TimeUnit.SECONDS);

        //8.发送验证码（模拟）
        log.debug("发送验证码成功，手机号为：{} 验证码：{}", phone, code);

        //9.返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.无效的手机号
            return Result.fail("无效的手机号");
        }

        //3.从Redis中获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        //4.校验验证码
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码过期或无效");
        }

        //5.判断数据库是否有该用户信息(MyBatisPlus)
        User user = query().eq("phone", phone).one();

        //6.没有则注册（保存用户信息到数据库）
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        //7.保存用户信息到Redis
        // 7.1随机生成token,作为用户令牌
        String token = UUID.randomUUID().toString(true);

        // 7.2把User转换为UserDTO在用HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 7.3将用户信息以hash数据结构存储到Redis
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        // 7.4设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8.返回token
        return Result.ok(token);
    }

    /**
     * 根据手机号创建用户并保存到数据库
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
