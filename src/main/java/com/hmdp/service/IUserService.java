package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码并保存
     * @param phone
     * @param request
     * @return
     */
    Result sendCode(String phone, HttpServletRequest request);

    /**
     * 登陆账户
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     *根据用户id查询用户信息
     * @param userId
     * @return
     */
    Result queryUserById(Long userId);

    /**
     * 签到
     * @return
     */
    Result sign();

    /**
     * 统计连续签到天数
     * @return
     */
    Result signCount();
}
