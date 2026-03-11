package nhnad.soeun_chat.domain.report.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.report.dto.ExcelExportResponse;
import nhnad.soeun_chat.domain.report.dto.ReportRequest;
import nhnad.soeun_chat.domain.report.dto.ReportResponse;
import nhnad.soeun_chat.domain.report.dto.ReportSummary;
import nhnad.soeun_chat.domain.report.service.ReportService;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.UnauthorizedException;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ReportController implements ReportApi {

    @Value("${internal.api.key}")
    private String internalApiKey;

    private final ReportService reportService;

    @Override
    public ApiResponse<ReportResponse> generateReport(
            @RequestHeader("X-Internal-Key") String internalKey,
            @RequestBody ReportRequest request) {

        if (!internalApiKey.equals(internalKey)) {
            log.warn("내부 API 키 인증 실패");
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
        }

        String userId = (request.targetUserId() != null) ? request.targetUserId() : "system";
        log.info("자동 리포트 요청 수신 - userId: {}, reportType: {}", userId, request.reportType());

        return ApiResponse.of(reportService.generateReport(userId, request));
    }

    @Override
    public ApiResponse<List<ReportSummary>> getReports(
            @AuthenticationPrincipal String userId) {

        log.info("리포트 목록 조회 - userId: {}", userId);
        return ApiResponse.of(reportService.getReports(userId));
    }

    @Override
    public ApiResponse<ExcelExportResponse> exportExcel(
            @AuthenticationPrincipal String userId,
            @PathVariable String conversationId) {

        log.info("Excel 내보내기 요청 - userId: {}, conversationId: {}", userId, conversationId);
        return ApiResponse.of(reportService.exportExcel(conversationId));
    }
}
