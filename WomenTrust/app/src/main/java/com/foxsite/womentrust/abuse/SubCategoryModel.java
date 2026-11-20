package com.foxsite.womentrust.abuse;

import java.io.Serializable;

public class SubCategoryModel implements Serializable {
    String subCategoryId;
    String subCategoryCode;
    String categoryName;
    String subCategoryName;

    public SubCategoryModel(String subCategoryId, String subCategoryCode, String categoryName, String subCategoryName) {
        this.subCategoryId = subCategoryId;
        this.subCategoryCode = subCategoryCode;
        this.categoryName = categoryName;
        this.subCategoryName = subCategoryName;
    }

    public String getSubCategoryId() {
        return subCategoryId;
    }

    public void setSubCategoryId(String subCategoryId) {
        this.subCategoryId = subCategoryId;
    }

    public String getSubCategoryCode() {
        return subCategoryCode;
    }

    public void setSubCategoryCode(String subCategoryCode) {
        this.subCategoryCode = subCategoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getSubCategoryName() {
        return subCategoryName;
    }

    public void setSubCategoryName(String subCategoryName) {
        this.subCategoryName = subCategoryName;
    }
}
