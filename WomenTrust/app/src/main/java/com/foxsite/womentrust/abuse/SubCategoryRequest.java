package com.foxsite.womentrust.abuse;

public class SubCategoryRequest {
    String Actions;
    String ViolenceCode;
    String ln;

    public SubCategoryRequest(String actions, String violenceCode, String ln) {
        Actions = actions;
        ViolenceCode = violenceCode;
        this.ln = ln;
    }
}
