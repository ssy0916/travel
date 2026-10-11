package org.example.dto;
import jakarta.validation.constraints.NotNull;
public class ChatRequestDTO {
    @NotNull(message = "message不能为空")
    private String message;
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
