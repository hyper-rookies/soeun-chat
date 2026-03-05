package nhnad.soeun_chat.domain.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.chat.repository.ConversationRepository;
import nhnad.soeun_chat.domain.chat.service.BedrockService;
import nhnad.soeun_chat.domain.conversation.service.ConversationService;
import nhnad.soeun_chat.domain.report.dto.ReportDocument;
import nhnad.soeun_chat.domain.report.dto.ReportRequest;
import nhnad.soeun_chat.domain.report.dto.ReportResponse;
import nhnad.soeun_chat.domain.report.dto.ReportSummary;
import nhnad.soeun_chat.domain.share.service.ShareService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Value("${aws.s3.bucket}")
    private String s3Bucket;

    private final ConversationRepository conversationRepository;
    private final BedrockService bedrockService;
    private final ConversationService conversationService;
    private final ShareService shareService;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    public ReportResponse generateReport(String userId, ReportRequest request) {
        String today = LocalDate.now().format(DATE_FORMAT);
        String conversationId = "report_" + today;
        String reportTitle = "주간 자동 리포트 " + today;

        log.info("자동 리포트 생성 시작 - conversationId: {}, userId: {}, reportType: {}", conversationId, userId, request.reportType());

        // 1. Bedrock Agentic Loop 실행 (SSE 없이 동기 처리)
        String userMessage = buildPrompt(request.reportType());
        SseEmitter dummyEmitter = new SseEmitter(0L);
        BedrockService.AgenticLoopResult loopResult =
                bedrockService.runAgenticLoop(dummyEmitter, userMessage, List.of());

        // 2. S3에 리포트 전체 내용 저장
        String s3Key = "reports/" + conversationId + ".json";
        saveReportToS3(s3Key, conversationId, userId, reportTitle, userMessage, loopResult);

        // 3. DynamoDB에 메타데이터 + S3 키만 저장 (messages 제외)
        conversationRepository.saveWithS3Key(conversationId, userId, reportTitle, s3Key);
        conversationService.updateUpdatedAt(conversationId, Instant.now().toEpochMilli());

        log.info("자동 리포트 생성 완료 - conversationId: {}, s3Key: {}", conversationId, s3Key);

        // 4. 공유 링크 토큰 생성 (만료 30일)
        String shareToken = shareService.generateSystemShareToken(conversationId);

        return new ReportResponse(shareToken, conversationId);
    }

    private void saveReportToS3(String s3Key, String conversationId, String userId,
                                String title, String userMessage,
                                BedrockService.AgenticLoopResult loopResult) {
        try {
            List<Object> structuredData = null;
            if (loopResult.structuredDataJson() != null && !loopResult.structuredDataJson().isBlank()) {
                structuredData = objectMapper.readValue(
                        loopResult.structuredDataJson(), new TypeReference<>() {});
            }

            String createdAt = Instant.now()
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .format(ISO_FORMAT);

            List<ReportDocument.ReportMessage> messages = List.of(
                    new ReportDocument.ReportMessage("user", userMessage, null),
                    new ReportDocument.ReportMessage("assistant", loopResult.answer(), structuredData)
            );

            ReportDocument doc = new ReportDocument(conversationId, userId, title, createdAt, messages);
            String json = objectMapper.writeValueAsString(doc);

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3Bucket)
                            .key(s3Key)
                            .contentType("application/json")
                            .build(),
                    RequestBody.fromString(json)
            );
            log.info("리포트 S3 저장 완료 - bucket: {}, key: {}", s3Bucket, s3Key);
        } catch (Exception e) {
            log.error("리포트 S3 저장 실패 - s3Key: {}", s3Key, e);
            throw new RuntimeException("리포트 S3 저장 중 오류 발생", e);
        }
    }

    public List<ReportSummary> getReports(String userId) {
        List<Map<String, AttributeValue>> items = conversationRepository.findReportsByUserId(userId);

        return items.stream().map(item -> {
            String conversationId = attr(item, "conversationId");
            String title = attrOrDefault(item, "title", "주간 자동 리포트");
            long createdAtMs = attrNOrDefault(item, "createdAt", Instant.now().toEpochMilli());
            String createdAt = Instant.ofEpochMilli(createdAtMs)
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .format(ISO_FORMAT);
            String shareToken = shareService.generateSystemShareToken(conversationId);
            return new ReportSummary(conversationId, title, createdAt, shareToken);
        }).toList();
    }

    private String buildPrompt(String reportType) {
        return "지난 한 주(2026-02-01~02-07) 구글과 카카오 광고 전체 성과를 요약해줘. " +
               "캠페인별 주요 지표, 매체 비교, 개선 제안을 포함해줘.";
    }

    private String attr(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null ? v.s() : null;
    }

    private String attrOrDefault(Map<String, AttributeValue> item, String key, String defaultValue) {
        AttributeValue v = item.get(key);
        return (v != null && v.s() != null) ? v.s() : defaultValue;
    }

    private Long attrNOrDefault(Map<String, AttributeValue> item, String key, Long defaultValue) {
        AttributeValue v = item.get(key);
        return (v != null && v.n() != null) ? Long.parseLong(v.n()) : defaultValue;
    }
}
