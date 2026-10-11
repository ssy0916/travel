package org.example.traveljavserver.vo;

import lombok.Data;

@Data
public class Result <T> {
    private Boolean success;
    private Integer code;
    private String message;
    private T data;
    private String error;
    private String rawResponse;
    public Boolean getSuccess() {
        return success;
    }
    public void setSuccess(Boolean success) {
        this.success = success;
    }
    public Integer getCode() {
        return code;
    }
    public void setCode(Integer code) {
        this.code = code;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public T getData() {
        return data;
    }
    public void setData(T data) {
        this.data = data;
    }
    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        // code 必须为 200：前端拦截器只认可 code===0/200，null 会被误判为失败
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }
    public static <T> Result<T> ok() {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode(200);
        result.setMessage("success");
        return result;
    }
    public static <T> Result<T> fail(String error, String message) {
        return fail(500, error, message);
    }

    public static <T> Result<T> fail(Integer code, String error, String message) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        result.setError(error);
        result.setRawResponse(null);
        return result;
    }
}
