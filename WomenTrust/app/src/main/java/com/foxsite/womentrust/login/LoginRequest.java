package com.foxsite.womentrust.login;

import java.io.Serializable;

public class LoginRequest implements Serializable {
    String Actions;
    String UserName;
    String Password;
    String ln;

    public LoginRequest(String actions, String userName, String password, String ln) {
        Actions = actions;
        UserName = userName;
        Password = password;
        this.ln = ln;
    }
}
