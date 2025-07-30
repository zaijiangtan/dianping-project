package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.无效的手机号
            return Result.fail("无效的手机号");
        }

        //3.手机号有效,生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存手机号和验证码
        session.setAttribute(SystemConstants.LOGIN_CODE + phone, code);

        //5.发送验证码（模拟）
        log.debug("发送验证码成功，手机号为：{} 验证码：{}", phone, code);

        //返回ok
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

        //3.从session中获取验证码
        String code = (String) session.getAttribute(SystemConstants.LOGIN_CODE + phone);

        //4.校验验证码
        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码过期或无效");
        }

        //5.判断数据库是否有该用户信息(MyBatisPlus)
        User user = query().eq("phone", phone).one();

        //6.没有则注册（保存用户信息到数据库）
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        //7.保存对话信息
        session.setAttribute(SystemConstants.USER, BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }

    /**
     * 根据手机号创建用户并保存到数据库
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
