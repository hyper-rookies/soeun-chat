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
            반드시 순수 SQL만 반환하세요. 설명, 한글 텍스트, 마크다운 코드블록은 절대 포함하지 마세요.

            [google_ad_performance 테이블]
            - 파티션: year, month, day
            - 주요 컬럼: camp_id, camp_name, camp_advertising_channel_type, camp_status,
                          agroup_id, agroup_name, keyword_id, keyword_text, keyword_match_type,
                          device, network_type, basic_date, adv_id, date, quarter, day_of_week, week
            - 성과 컬럼(bigint): impressions, clicks, video_views, all_conversions, conversions
            - 성과 컬럼(double): cost_micros, ctr, average_cpc, all_conversions_value,
                                  conversions_value, value_per_conversion, cost_per_conversion,
                                  conversions_from_interactions_rate,
                                  video_quartile_p25_rate, video_quartile_p50_rate,
                                  video_quartile_p75_rate, video_quartile_p100_rate

            [kakao_ad_performance 테이블]
            - 파티션: year, month, day
            - 주요 컬럼: kwd_id, kwd_name, kwd_config, kwd_url, kwd_bid_type, kwd_bid_amount,
                          agroup_id, agroup_name, camp_id, camp_name, camp_type,
                          biz_id, biz_name, basic_date, adv_id
            - 성과 컬럼(bigint): imp, click, rimp, rank,
                                  conv_cmpt_reg_1d, conv_cmpt_reg_7d,
                                  conv_view_cart_1d, conv_view_cart_7d,
                                  conv_purchase_1d, conv_purchase_7d,
                                  conv_participation_1d, conv_participation_7d,
                                  conv_signup_1d, conv_signup_7d,
                                  conv_app_install_1d, conv_app_install_7d
            - 성과 컬럼(double): spending, ctr, ppc, conv_purchase_p_1d, conv_purchase_p_7d

            쿼리 작성 규칙:
            - 항상 파티션 컬럼(year, month, day)을 WHERE 조건에 포함하세요.
            - 데이터베이스명을 명시하세요: se_report_db.google_ad_performance 또는 se_report_db.kakao_ad_performance
            - 날짜 필터가 없으면 최근 30일 기준으로 작성하세요.
            - 두 테이블이 모두 필요하면 UNION ALL을 사용하세요.
            - ★중요★: UNION ALL을 사용할 때, 개별 SELECT 문 안에는 절대 ORDER BY를 사용하지 마세요. 정렬이 필요하다면 서브쿼리로 감싸거나 쿼리 맨 마지막에 한 번만 작성하세요.
            - 광고 데이터 분석과 무관한 질문일 경우, 쿼리를 작성하지 말고 오직 다음 단어 하나만 반환하세요: INVALID
            """;

    private static final String ANSWER_SYSTEM_PROMPT = """
            당신은 광고 성과 분석 전문가입니다.
            쿼리 결과를 바탕으로 사용자의 질문에 친절하고 명확하게 답변하세요.
            숫자는 가독성 있게 포맷팅하고, 핵심 인사이트를 제공하세요.
            """;

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