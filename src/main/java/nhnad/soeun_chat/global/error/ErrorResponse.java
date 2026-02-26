package nhnad.soeun_chat.global.error;

import lombok.Getter;

@Getter
public class ErrorResponse {

    private final boolean success = false;
    private final String code;
    private final String message;

    private ErrorResponse(ErrorCode errorCode) {
        this.code = errorCode.name();
        this.message = errorCode.getMessage();
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode);
    }
}