package nhnad.soeun_chat.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.chat.dto.ChatMessage;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.InternalServerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class BedrockService {

    @Value("${aws.bedrock.model-id}")
    private String modelId;

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

    private static final String SQL_SYSTEM_PROMPT = """
            당신은 AWS Athena SQL 전문가입니다. 사용자의 자연어 질문을 Athena SQL로 변환하세요.

            테이블: se_report_db.se_ad_performance_parquet
            컬럼: date(string), campaign_id(string), campaign_name(string),
                  ad_group_id(string), ad_group_name(string),
                  impressions(bigint), clicks(bigint), cost(double),
                  conversions(bigint), conversion_value(double),
                  platform(string, 파티션), year(string, 파티션),
                  month(string, 파티션), day(string, 파티션)

            규칙:
            - SQL 쿼리만 반환하세요. 마크다운 코드블록 없이 순수 SQL만.
            - 파티션 컬럼(platform, year, month, day)을 WHERE 조건에 활용하세요.
            - 날짜 범위가 없으면 최근 30일 데이터를 조회하세요.
            """;

    private static final String ANSWER_SYSTEM_PROMPT = """
            당신은 광고 성과 분석 전문가입니다.
            쿼리 결과를 바탕으로 사용자의 질문에 친절하고 명확하게 답변하세요.
            숫자는 가독성 있게 포맷팅하고, 핵심 인사이트를 제공하세요.
            """;

    /**
     * 사용자 질문으로 Athena SQL을 생성합니다 (동기, non-streaming).
     */
    public String generateSql(String userMessage, List<ChatMessage> history) {
        List<Message> messages = buildConverseMessages(userMessage, history);

        ConverseResponse response = bedrockRuntimeClient.converse(
                ConverseRequest.builder()
                        .modelId(modelId)
                        .system(SystemContentBlock.fromText(SQL_SYSTEM_PROMPT))
                        .messages(messages)
                        .build()
        );

        return response.output().message().content().get(0).text().trim();
    }

    /**
     * 최종 답변을 SseEmitter로 스트리밍하고, 전체 응답 텍스트를 반환합니다.
     */
    public String streamAnswer(SseEmitter emitter,
                               String userMessage,
                               String queryResult,
                               List<ChatMessage> history) {
        String userPrompt = String.format("질문: %s\n\n쿼리 결과:\n%s", userMessage, queryResult);
        List<Message> messages = buildConverseMessages(userPrompt, history);

        StringBuilder fullAnswer = new StringBuilder();

        ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                        .onContentBlockDelta(event -> {
                            String text = event.delta().text();
                            if (text != null && !text.isEmpty()) {
                                fullAnswer.append(text);
                                try {
                                    emitter.send(SseEmitter.event().data(text));
                                } catch (Exception e) {
                                    log.warn("SSE 전송 실패: {}", e.getMessage());
                                }
                            }
                        })
                        .build())
                .build();

        try {
            bedrockRuntimeAsyncClient.converseStream(
                    ConverseStreamRequest.builder()
                            .modelId(modelId)
                            .system(SystemContentBlock.fromText(ANSWER_SYSTEM_PROMPT))
                            .messages(messages)
                            .build(),
                    handler
            ).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServerException(ErrorCode.CHAT_PROCESSING_ERROR);
        } catch (ExecutionException e) {
            log.error("Bedrock 스트리밍 실패: {}", e.getMessage());
            throw new InternalServerException(ErrorCode.CHAT_PROCESSING_ERROR);
        }

        return fullAnswer.toString();
    }

    private List<Message> buildConverseMessages(String userMessage, List<ChatMessage> history) {
        List<Message> messages = new ArrayList<>();

        for (ChatMessage h : history) {
            messages.add(Message.builder()
                    .role(ConversationRole.fromValue(h.role()))
                    .content(ContentBlock.fromText(h.content()))
                    .build());
        }

        messages.add(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userMessage))
                .build());

        return messages;
    }
}
