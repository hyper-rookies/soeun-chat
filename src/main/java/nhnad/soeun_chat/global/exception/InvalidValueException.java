package nhnad.soeun_chat.global.exception;

import nhnad.soeun_chat.global.error.ErrorCode;

public class InvalidValueException extends BusinessException {
    public InvalidValueException() {
        super(ErrorCode.INVALID_INPUT);
    }
    public InvalidValueException(ErrorCode errorCode) {
        super(errorCode);
    }
}