package nhnad.soeun_chat.domain.report.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.report.dto.ReportRequest;
import nhnad.soeun_chat.domain.report.dto.ReportResponse;
import nhnad.soeun_chat.domain.report.service.ReportService;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.UnauthorizedException;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ReportController {

    @Value("${internal.api.key}")
    private String internalApiKey;

    private final ReportService reportService;

    @PostMapping("/report")
    public ApiResponse<ReportResponse> generateReport(
            @RequestHeader("X-Internal-Key") String internalKey,
            @RequestBody ReportRequest request) {

        if (!internalApiKey.equals(internalKey)) {
            log.warn("내부 API 키 인증 실패");
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
        }

        log.info("자동 리포트 요청 수신 - reportType: {}", request.reportType());
        ReportResponse response = reportService.generateReport(request);
        return ApiResponse.of(response);
    }
}
