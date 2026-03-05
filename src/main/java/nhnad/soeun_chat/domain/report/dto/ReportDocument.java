package nhnad.soeun_chat.domain.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * S3에 저장되는 리포트 전체 문서 구조
 * Key: reports/report_{yyyyMMdd}.json
 */
public record ReportDocument(
        String conversationId,
        String userId,
        String title,
        String createdAt,
        List<ReportMessage> messages
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReportMessage(
            String role,
            String content,
            List<Object> structuredData
    ) {}
}
