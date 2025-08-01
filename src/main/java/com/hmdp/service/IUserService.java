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
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpServletRequest request);

    /**
     * 登陆账户
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);
}
