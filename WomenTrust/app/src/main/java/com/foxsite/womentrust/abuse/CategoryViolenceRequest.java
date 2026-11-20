package com.foxsite.womentrust.abuse;

public class CategoryViolenceRequest {
    String Actions;
    String ViolenceCode;
    String SubViolenceCode;
    String ln;

    public CategoryViolenceRequest(String actions, String violenceCode, String subViolenceCode, String ln) {
        Actions = actions;
        ViolenceCode = violenceCode;
        SubViolenceCode = subViolenceCode;
        this.ln = ln;
    }
}
