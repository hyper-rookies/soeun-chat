package nhnad.soeun_chat.global.exception;

import nhnad.soeun_chat.global.error.ErrorCode;

public class InternalServerException extends BusinessException {
    public InternalServerException() {
        super(ErrorCode.INTERNAL_SERVER_ERROR);
    }
    public InternalServerException(ErrorCode errorCode) {
        super(errorCode);
    }
}