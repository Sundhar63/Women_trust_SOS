package com.foxsite.womentrust.register;

public class UserRegisterModel {
    String NAME;
    String MOBILE;
    String ALTERNATIVE_MOBILE;
    String EMAIL;
    String AADHAR_NO;
    String ADDRESS;
    String USERNAMES;
    String PASSWORDS;
    String DEVICE_ID;
    String MASTER_STATUS;
    String USERNAME;
    String ROLE;
    String UPDATED;
    String UPDATETIME;

    public UserRegisterModel(String NAME, String MOBILE, String ALTERNATIVE_MOBILE, String EMAIL, String AADHAR_NO, String ADDRESS, String USERNAMES, String PASSWORDS, String DEVICE_ID, String MASTER_STATUS, String USERNAME, String ROLE, String UPDATED, String UPDATETIME) {
        this.NAME = NAME;
        this.MOBILE = MOBILE;
        this.ALTERNATIVE_MOBILE = ALTERNATIVE_MOBILE;
        this.EMAIL = EMAIL;
        this.AADHAR_NO = AADHAR_NO;
        this.ADDRESS = ADDRESS;
        this.USERNAMES = USERNAMES;
        this.PASSWORDS = PASSWORDS;
        this.DEVICE_ID = DEVICE_ID;
        this.MASTER_STATUS = MASTER_STATUS;
        this.USERNAME = USERNAME;
        this.ROLE = ROLE;
        this.UPDATED = UPDATED;
        this.UPDATETIME = UPDATETIME;
    }
}
