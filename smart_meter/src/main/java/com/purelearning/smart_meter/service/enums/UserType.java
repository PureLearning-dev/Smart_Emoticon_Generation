package com.purelearning.smart_meter.service.enums;

public enum UserType {
    NORMAL_USER(1, "普通用户"),
    ADMIN(2, "管理员");

    private final int code;
    private final String desc;

    UserType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static UserType fromCode(int code) {
        for (UserType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid UserType code: " + code);
    }

}
