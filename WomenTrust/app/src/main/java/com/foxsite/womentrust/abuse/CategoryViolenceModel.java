package com.foxsite.womentrust.abuse;

import java.io.Serializable;

public class CategoryViolenceModel implements Serializable {
    String violenceId;
    String violenceCode;
    String violenceTypeCode;
    String subViolenceTypeCode;
    String violenceName;
    String violenceStatus;

    public CategoryViolenceModel(String violenceId, String violenceCode, String violenceTypeCode, String subViolenceTypeCode, String violenceName, String violenceStatus) {
        this.violenceId = violenceId;
        this.violenceCode = violenceCode;
        this.violenceTypeCode = violenceTypeCode;
        this.subViolenceTypeCode = subViolenceTypeCode;
        this.violenceName = violenceName;
        this.violenceStatus = violenceStatus;
    }

    public String getViolenceId() {
        return violenceId;
    }

    public void setViolenceId(String violenceId) {
        this.violenceId = violenceId;
    }

    public String getViolenceCode() {
        return violenceCode;
    }

    public void setViolenceCode(String violenceCode) {
        this.violenceCode = violenceCode;
    }

    public String getViolenceTypeCode() {
        return violenceTypeCode;
    }

    public void setViolenceTypeCode(String violenceTypeCode) {
        this.violenceTypeCode = violenceTypeCode;
    }

    public String getSubViolenceTypeCode() {
        return subViolenceTypeCode;
    }

    public void setSubViolenceTypeCode(String subViolenceTypeCode) {
        this.subViolenceTypeCode = subViolenceTypeCode;
    }

    public String getViolenceName() {
        return violenceName;
    }

    public void setViolenceName(String violenceName) {
        this.violenceName = violenceName;
    }

    public String getViolenceStatus() {
        return violenceStatus;
    }

    public void setViolenceStatus(String violenceStatus) {
        this.violenceStatus = violenceStatus;
    }
}
