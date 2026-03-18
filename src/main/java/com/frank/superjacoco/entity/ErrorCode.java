package com.frank.superjacoco.entity;

import org.apache.commons.lang3.StringUtils;

public enum ErrorCode {
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    TOO_MANY_REQUESTS(429, "too many requests"),
    FAIL(-1, "fail");


    private int code;
    private String msg;

    private ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return this.code;
    }

    public String getMsg() {
        return this.msg;
    }

    public String getMsg(String msg) {
        return StringUtils.isBlank(msg) ? this.msg : this.msg + ": " + msg;
    }
}


