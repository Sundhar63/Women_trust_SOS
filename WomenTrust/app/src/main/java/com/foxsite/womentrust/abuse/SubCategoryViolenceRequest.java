package com.foxsite.womentrust.abuse;

public class SubCategoryViolenceRequest {
    String Actions;
    String ViolenceCode;
    String SubViolenceCode;
    String CategoryCode;
    String ln;

    public SubCategoryViolenceRequest(String actions, String violenceCode, String subViolenceCode, String categoryCode, String ln) {
        Actions = actions;
        ViolenceCode = violenceCode;
        SubViolenceCode = subViolenceCode;
        CategoryCode = categoryCode;
        this.ln = ln;
    }
}
