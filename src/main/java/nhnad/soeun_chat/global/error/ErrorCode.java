package nhnad.soeun_chat.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    MISSING_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "필수 요청 파라미터가 누락되었습니다."),

    // 인증
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    // 광고 계정
    AD_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "광고 계정을 찾을 수 없습니다."),
    AD_ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 연동된 광고 계정입니다."),

    // 채팅
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화를 찾을 수 없습니다."),
    CHAT_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "채팅 처리 중 오류가 발생했습니다."),
    SQL_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SQL 생성 중 오류가 발생했습니다."),
    ATHENA_QUERY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "데이터 조회 중 오류가 발생했습니다."),

    // 리포트
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "리포트를 찾을 수 없습니다."),
    REPORT_EXPIRED(HttpStatus.GONE, "만료된 리포트입니다.");

    private final HttpStatus status;
    private final String message;
}