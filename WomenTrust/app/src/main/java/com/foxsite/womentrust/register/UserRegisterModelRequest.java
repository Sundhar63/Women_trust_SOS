package com.foxsite.womentrust.register;

import java.util.List;

public class UserRegisterModelRequest {
    public String Actions;
    public String ln;
    public List<UserRegisterModel> LOGIN_MASTERs;

    public UserRegisterModelRequest(String actions, String ln, List<UserRegisterModel> LOGIN_MASTERs) {
        Actions = actions;
        this.ln = ln;
        this.LOGIN_MASTERs = LOGIN_MASTERs;
    }
}
