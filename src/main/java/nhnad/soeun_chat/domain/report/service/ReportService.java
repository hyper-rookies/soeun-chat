package nhnad.soeun_chat.domain.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.chat.repository.ConversationRepository;
import nhnad.soeun_chat.domain.chat.service.BedrockService;
import nhnad.soeun_chat.domain.conversation.service.ConversationService;
import nhnad.soeun_chat.domain.report.dto.ExcelExportResponse;
import nhnad.soeun_chat.domain.report.dto.ReportDocument;
import nhnad.soeun_chat.domain.report.dto.ReportRequest;
import nhnad.soeun_chat.domain.report.dto.ReportResponse;
import nhnad.soeun_chat.domain.report.dto.ReportSummary;
import nhnad.soeun_chat.domain.share.service.ShareService;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.InternalServerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
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

    @Value("${excel.lambda.function-name}")
    private String excelLambdaFunctionName;

    private final ConversationRepository conversationRepository;
    private final BedrockService bedrockService;
    private final ConversationService conversationService;
    private final ShareService shareService;
    private final S3Client s3Client;
    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;

    public ReportResponse generateReport(String userId, ReportRequest request) {
        LocalDate now = LocalDate.now();
        String today = now.format(DATE_FORMAT);
        String conversationId = "report_" + today + "_" + userId;
        String reportTitle = generateReportTitle(now);

        log.info("자동 리포트 생성 시작 - conversationId: {}, userId: {}, reportType: {}", conversationId, userId, request.reportType());

        // 1. Bedrock Agentic Loop 실행 (SSE 없이 동기 처리)
        String userMessage = buildPrompt(request.reportType());
        SseEmitter dummyEmitter = new SseEmitter(0L);
        BedrockService.AgenticLoopResult loopResult =
                bedrockService.runAgenticLoop(dummyEmitter, userMessage, List.of(), true);

        // 2. S3에 리포트 전체 내용 저장
        String s3Key = "reports/report_" + today + "_" + userId + ".json";
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
            String createdAt = Instant.now()
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .format(ISO_FORMAT);

            List<ReportDocument.ReportMessage> messages = new java.util.ArrayList<>();
            messages.add(new ReportDocument.ReportMessage("user", userMessage, null, null));

            for (BedrockService.ChartData chart : loopResult.chartDataList()) {
                List<Object> data = objectMapper.readValue(chart.dataJson(), new TypeReference<>() {});
                messages.add(new ReportDocument.ReportMessage("assistant", "", chart.chartType(), data));
            }

            messages.add(new ReportDocument.ReportMessage("assistant", loopResult.answer(), null, null));

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

    private String generateReportTitle(LocalDate date) {
        int weekOfMonth = (date.getDayOfMonth() - 1) / 7 + 1;
        return String.format("%d년 %d월 %d주차 주간 리포트",
                date.getYear(), date.getMonthValue(), weekOfMonth);
    }

    private String buildPrompt(String reportType) {
        return """
                [ABSOLUTE RULE - NEVER VIOLATE]
                1. 섹션 제목(##, ###)에 이모지 절대 사용 금지
                2. bullet point 앞에 이모지 절대 사용 금지
                3. 허용되는 섹션 제목: "## 성과 요약", "## 주요 인사이트", "## 개선 제안" 만 사용
                4. 소제목은 ### 없이 "구글 광고", "카카오 광고" 같은 텍스트만 사용
                5. 텍스트 어디에도 이모지(Unicode emoji) 문자를 출력하지 마세요.

                지난 한 주(2026-02-01~02-07) 구글과 카카오 광고 전체 성과를 분석해줘.
                캠페인별 주요 지표, 매체 비교, 개선 제안을 포함해줘.

                응답은 반드시 아래 3개 섹션을 ## 헤더로 구분해서 작성하세요.
                섹션 제목에 볼드(**) 사용 금지.

                ## 성과 요약
                (전체 매체 통합 성과를 3~5줄 요약)

                ## 주요 인사이트
                (구글/카카오 각 캠페인별 핵심 발견사항을 bullet point로)

                ## 개선 제안
                (구체적인 액션 아이템을 bullet point로)
                """;
    }

    public ExcelExportResponse exportExcel(String conversationId) {
        try {
            String payloadJson = objectMapper.writeValueAsString(Map.of("conversationId", conversationId));

            InvokeRequest invokeRequest = InvokeRequest.builder()
                    .functionName(excelLambdaFunctionName)
                    .payload(SdkBytes.fromUtf8String(payloadJson))
                    .build();

            InvokeResponse invokeResponse = lambdaClient.invoke(invokeRequest);

            String responseJson = invokeResponse.payload().asUtf8String();
            JsonNode root = objectMapper.readTree(responseJson);

            int statusCode = root.path("statusCode").asInt();
            if (statusCode != 200) {
                log.error("Excel Lambda 오류 응답 - statusCode: {}, body: {}", statusCode, root.path("body").asText());
                throw new InternalServerException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            JsonNode body = objectMapper.readTree(root.path("body").asText());
            String downloadUrl = body.path("downloadUrl").asText();
            String fileName = body.path("fileName").asText();

            return new ExcelExportResponse(downloadUrl, fileName);
        } catch (InternalServerException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel Lambda invoke 실패 - conversationId: {}", conversationId, e);
            throw new InternalServerException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
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
