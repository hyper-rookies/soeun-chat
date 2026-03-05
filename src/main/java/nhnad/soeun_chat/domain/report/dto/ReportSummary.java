package nhnad.soeun_chat.domain.report.dto;

public record ReportSummary(
        String conversationId,
        String title,
        String createdAt,
        String shareToken
) {}
