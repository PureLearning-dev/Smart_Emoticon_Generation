package com.purelearning.smart_meter.service.enums;

public enum UserStatus {
    DISABLED(0, "禁用"),
    NORMAL(1, "正常");

    private final int code;
    private final String desc;

    UserStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static UserStatus fromCode(int code) {
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid UserStatus code: " + code);
    }
}
