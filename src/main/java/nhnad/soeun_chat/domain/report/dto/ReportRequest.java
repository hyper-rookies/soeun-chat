package nhnad.soeun_chat.domain.report.dto;

public record ReportRequest(
        String reportType,
        String targetUserId
) {
}
