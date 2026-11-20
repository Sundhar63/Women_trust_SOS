package com.foxsite.womentrust.abuse;

import com.foxsite.womentrust.register.UserRegisterModel;

import java.util.List;

public class AbuseMasterRequest {
    public String Actions;
    public String ln;
    public List<AddAbuseDetailRequest> aBUSES_MASTER;

    public AbuseMasterRequest(String actions, String ln, List<AddAbuseDetailRequest> aBUSES_MASTER) {
        Actions = actions;
        this.ln = ln;
        this.aBUSES_MASTER = aBUSES_MASTER;
    }
}
