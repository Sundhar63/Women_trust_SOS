package com.foxsite.womentrust.abuse;

public class SubCategoryViolenceModel {
    String id;
    String code;
    String violence_code;
    String sub_violence_code;
    String category_code;
    String sub_category;
    String master_status;
    String username;
    String role;
    String updated;
    String updatetime;

    public SubCategoryViolenceModel(String id, String code, String violence_code, String sub_violence_code, String category_code, String sub_category, String master_status, String username, String role, String updated, String updatetime) {
        this.id = id;
        this.violence_code = violence_code;
        this.code = code;
        this.sub_violence_code = sub_violence_code;
        this.category_code = category_code;
        this.sub_category = sub_category;
        this.master_status = master_status;
        this.username = username;
        this.role = role;
        this.updated = updated;
        this.updatetime = updatetime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getViolence_code() {
        return violence_code;
    }

    public void setViolence_code(String violence_code) {
        this.violence_code = violence_code;
    }

    public String getSub_violence_code() {
        return sub_violence_code;
    }

    public void setSub_violence_code(String sub_violence_code) {
        this.sub_violence_code = sub_violence_code;
    }

    public String getCategory_code() {
        return category_code;
    }

    public void setCategory_code(String category_code) {
        this.category_code = category_code;
    }

    public String getSub_category() {
        return sub_category;
    }

    public void setSub_category(String sub_category) {
        this.sub_category = sub_category;
    }

    public String getMaster_status() {
        return master_status;
    }

    public void setMaster_status(String master_status) {
        this.master_status = master_status;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public String getUpdatetime() {
        return updatetime;
    }

    public void setUpdatetime(String updatetime) {
        this.updatetime = updatetime;
    }
}
