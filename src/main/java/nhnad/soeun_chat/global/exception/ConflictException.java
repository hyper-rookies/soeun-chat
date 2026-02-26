package nhnad.soeun_chat.global.exception;

import nhnad.soeun_chat.global.error.ErrorCode;

public class ConflictException extends BusinessException {
    public ConflictException() {
        super(ErrorCode.CONFLICT);
    }
    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }
}