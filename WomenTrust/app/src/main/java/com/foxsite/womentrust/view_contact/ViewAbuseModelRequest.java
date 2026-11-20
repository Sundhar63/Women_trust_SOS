package com.foxsite.womentrust.view_contact;

public class ViewAbuseModelRequest {
    String Actions;
    String ViolenceName;
    String SubViolenceName;
    String CategoryName;
    String ln;

    public ViewAbuseModelRequest(String actions, String violenceName, String subViolenceName, String categoryName, String ln) {
        Actions = actions;
        ViolenceName = violenceName;
        SubViolenceName = subViolenceName;
        CategoryName = categoryName;
        this.ln = ln;
    }
}
