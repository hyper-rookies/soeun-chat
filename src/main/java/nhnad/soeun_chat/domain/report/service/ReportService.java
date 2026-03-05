package nhnad.soeun_chat.domain.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.chat.repository.ConversationRepository;
import nhnad.soeun_chat.domain.chat.repository.MessageRepository;
import nhnad.soeun_chat.domain.chat.service.BedrockService;
import nhnad.soeun_chat.domain.conversation.service.ConversationService;
import nhnad.soeun_chat.domain.report.dto.ReportRequest;
import nhnad.soeun_chat.domain.report.dto.ReportResponse;
import nhnad.soeun_chat.domain.share.service.ShareService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String SYSTEM_USER_ID = "system";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final BedrockService bedrockService;
    private final ConversationService conversationService;
    private final ShareService shareService;

    public ReportResponse generateReport(ReportRequest request) {
        String today = LocalDate.now().format(DATE_FORMAT);
        String conversationId = "report_" + today;
        String reportTitle = "주간 자동 리포트 " + today;

        log.info("자동 리포트 생성 시작 - conversationId: {}, reportType: {}", conversationId, request.reportType());

        // 1. 대화 생성
        if (conversationRepository.findById(conversationId).isEmpty()) {
            conversationRepository.save(conversationId, SYSTEM_USER_ID, reportTitle);
        }

        // 2. Bedrock Agentic Loop 실행 (SSE 없이 동기 처리)
        String userMessage = buildPrompt(request.reportType());
        SseEmitter dummyEmitter = new SseEmitter(0L);
        BedrockService.AgenticLoopResult loopResult =
                bedrockService.runAgenticLoop(dummyEmitter, userMessage, List.of());

        // 3. 메시지 DynamoDB 저장
        messageRepository.save(conversationId, "user", userMessage, null);
        messageRepository.save(conversationId, "assistant", loopResult.answer(), loopResult.structuredDataJson());
        conversationService.updateUpdatedAt(conversationId, Instant.now().toEpochMilli());

        log.info("자동 리포트 생성 완료 - conversationId: {}", conversationId);

        // 4. 공유 링크 토큰 생성 (만료 30일)
        String shareToken = shareService.generateSystemShareToken(conversationId);

        return new ReportResponse(shareToken, conversationId);
    }

    private String buildPrompt(String reportType) {
        return "지난 한 주(2026-02-01~02-07) 구글과 카카오 광고 전체 성과를 요약해줘. " +
               "캠페인별 주요 지표, 매체 비교, 개선 제안을 포함해줘.";
    }
}
