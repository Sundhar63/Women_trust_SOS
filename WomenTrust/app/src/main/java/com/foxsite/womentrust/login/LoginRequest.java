package com.foxsite.womentrust.login;

import java.io.Serializable;

public class LoginRequest implements Serializable {
    String Actions;
    String UserName;
    String Password;
    String DeviceId;
    String ln;

    public LoginRequest(String actions, String userName, String password, String ln) {
        this(actions, userName, password, null, ln);
    }

    public LoginRequest(String actions, String userName, String password, String deviceId, String ln) {
        Actions = actions;
        UserName = userName;
        Password = password;
        DeviceId = deviceId;
        this.ln = ln;
    }
}
