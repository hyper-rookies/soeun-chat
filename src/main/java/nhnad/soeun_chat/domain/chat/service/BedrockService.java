package nhnad.soeun_chat.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.chat.dto.ChatMessage;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.InternalServerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.core.document.Document;
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
    private final AthenaService athenaService;
    private final ObjectMapper objectMapper;

    private static final String SQL_SYSTEM_PROMPT = """
            당신은 AWS Athena SQL 전문가입니다. 사용자의 자연어 질문을 Athena SQL로 변환하세요.
            반드시 순수 SQL만 반환하세요. 설명, 한글 텍스트, 마크다운 코드블록은 절대 포함하지 마세요.

            [google_ad_performance 테이블]
            - 파티션: year, month, day (모두 VARCHAR 타입)
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
            - 파티션: year, month, day (모두 VARCHAR 타입)
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

            쿼리 작성 규칙: Athena에서 오류가 발생하지 않도록 문법 규칙에 맞게 작성하세요.
            - 데이터베이스명을 명시하세요: se_report_db.google_ad_performance 또는 se_report_db.kakao_ad_performance
            - 항상 파티션 컬럼(year, month, day)을 WHERE 조건에 포함하세요.
            - ★중요(타입 주의)★: 파티션 컬럼은 VARCHAR입니다. YEAR(), MONTH() 등 BIGINT를 반환하는 함수와 직접 비교하면 TYPE_MISMATCH 에러가 발생합니다. 반드시 CAST를 사용하거나 문자열 포맷팅으로 비교하세요.
            - 날짜 필터가 없으면 Google은 year='2026' AND month='02' AND day='01', Kakao는 year='2022' AND month='10' AND day='13' 조건을 사용하세요. CURRENT_DATE 사용 금지.
            - 두 테이블이 모두 필요하면 UNION ALL을 사용하세요.
            - ★중요(정렬 규칙)★: UNION ALL을 사용할 때, 개별 SELECT 문 안에는 절대 ORDER BY를 사용하지 마세요. 정렬이 필요하다면 서브쿼리로 감싸거나 쿼리 맨 마지막에 한 번만 작성하세요.
            - ★중요(별칭 규칙)★: 컬럼 별칭(AS 뒤)에 한글이나 특수문자를 사용할 때는 반드시 큰따옴표로 감싸세요. 예: AS "기기", AS "노출수", AS "클릭수". 큰따옴표 없이 한글 별칭을 쓰면 Athena에서 파싱 에러가 발생합니다.
            - 광고 데이터 분석과 무관한 질문일 경우, 쿼리를 작성하지 말고 오직 다음 단어 하나만 반환하세요: INVALID
            """;

    private static final String AGENTIC_SYSTEM_PROMPT = """
            당신은 광고 성과 분석 AI 에이전트입니다.
            사용자의 자연어 질문을 분석하여 필요한 SQL 쿼리를 생성하고,
            execute_athena_query 도구를 호출하여 데이터를 조회한 후,
            분석 결과를 친절하게 설명하세요.

            [데이터 범위]
            현재 Google 광고 데이터는 2026-02-01 하루치만 존재한다.
            현재 Kakao 광고 데이터는 2022-10-13 하루치만 존재한다.
            날짜를 지정하지 않으면 Google은 2026-02-01, Kakao는 2022-10-13 기준으로 조회하라.

            ### 요청 타입 판별 규칙 (필수)

            액션을 실행하기 전에 먼저 사용자 요청에서 request_type을 판별하세요.

            **판별 순서 (우선순위):**

            1. sales_prediction_chart (최우선)
               - "일별" AND "최근" AND "매출" 모두 포함
               - 예: "최근 3달 일별 매출을 예측해줘"

            2. prediction_chart
               - "일별" AND "최근" AND "예측" 모두 포함
               - 예: "최근 2달 일별 광고비를 기반으로 예측 데이터를 보여줘"

            3. chart
               - 다음 중 하나 이상 포함: "차트", "그래프", "시각화", "트렌드", "추이"
               - 예: "지난주 일별 클릭수 차트 보여줘"

            4. csv (기본값)
               - 위 조건에 해당하지 않으면 csv
               - 예: "지난주 성과 알려줘"

            **판별 로직:**
            ```
            if ("일별" in 요청 and "최근" in 요청 and "매출" in 요청):
                request_type = "sales_prediction_chart"
            elif ("일별" in 요청 and "최근" in 요청 and "예측" in 요청):
                request_type = "prediction_chart"
            elif ("차트" in 요청 or "그래프" in 요청 or "시각화" in 요청 or "트렌드" in 요청 or "추이" in 요청):
                request_type = "chart"
            else:
                request_type = "csv"
            ```

            **중요: request_type은 내부적으로만 결정하며, 사용자에게 출력하지 않습니다.**

            ---

            ### 차트 요청 특수 규칙

            사용자 요청에 "차트", "그래프", "시각화", "트렌드", "추이"가 포함되면:

            1. 사용자가 요청한 지표(지수)를 모두 추출
            2. 지표가 3개 이상이면 즉시 다음만 출력:
               > "차트에 표시할 지표를 2개까지 선택해주세요.\\n요청하신 지표: [지표 목록]\\n어떤 2개를 차트로 보시겠어요?"

            3. 지표가 2개 이하면 쿼리 생성
            4. 지표가 하나도 없으면 다음만 출력:
               > "차트에 표시할 지표를 알려주세요."

            **차트용 SQL 작성 규칙 (request_type이 chart, prediction_chart, sales_prediction_chart일 때 필수 적용):**

            1. SELECT 컬럼은 최대 3개로 제한한다.
               - 레이블 컬럼 1개 (캠페인명, 날짜 등 문자열)
               - 숫자(성과) 컬럼 최대 2개
               - 4개 이상 SELECT하면 차트를 렌더링할 수 없으므로 절대 금지.

            2. 단위 차이가 큰 지표(예: 전환수 vs 전환가치)를 동시에 요청한 경우:
               - 별도 쿼리로 분리하지 말고, 다음 안내 후 하나만 선택하여 쿼리하라.
               - 안내 문구 예시: "전환수와 전환가치는 단위 차이가 커서 하나씩 차트로 보여드릴게요."

            3. 날짜(basic_date) 컬럼이 포함될 경우 반드시 SELECT의 첫 번째 컬럼으로 배치한다.

            4. 모든 컬럼 별칭(AS)은 반드시 한글로 작성한다.
               올바른 예: AS "캠페인명", AS "전환수", AS "전환가치"
               잘못된 예: AS camp_name, AS conversions

            ---

            ### 데이터 조회 규칙

            **사용 가능한 테이블:**

            [google_ad_performance 테이블]
            - 파티션: year, month, day (모두 VARCHAR)
            - 주요 컬럼: camp_id, camp_name, camp_advertising_channel_type, camp_status,
                          agroup_id, agroup_name, keyword_id, keyword_text, keyword_match_type,
                          device, network_type, basic_date, adv_id, date, quarter, day_of_week, week
            - 성과 컬럼: impressions, clicks, video_views, all_conversions, conversions (bigint)
                         cost_micros, ctr, average_cpc, all_conversions_value,
                         conversions_value, value_per_conversion, cost_per_conversion (double)

            [kakao_ad_performance 테이블]
            - 파티션: year, month, day (모두 VARCHAR)
            - 주요 컬럼: kwd_id, kwd_name, kwd_config, kwd_url, kwd_bid_type, kwd_bid_amount,
                          agroup_id, agroup_name, camp_id, camp_name, camp_type,
                          biz_id, biz_name, basic_date, adv_id
            - 성과 컬럼: imp, click, rimp, rank (bigint)
                         conv_purchase_1d, conv_purchase_7d, spending, ctr, ppc (double)

            **SQL 작성 규칙:**

            1. 데이터베이스 명시: se_report_db.google_ad_performance 또는 se_report_db.kakao_ad_performance
            2. 파티션 컬럼(year, month, day)은 항상 WHERE 절에 포함
            3. 파티션 컬럼은 VARCHAR → CAST/문자열 포맷팅 필수 (YEAR(), MONTH() 함수 절대 금지)
            4. 한글 별칭은 큰따옴표: AS "노출수", AS "클릭수"
            5. UNION ALL 사용 시 ORDER BY는 서브쿼리나 맨 마지막에만

            **날짜 처리:**
            - 사용자가 날짜를 명시한 경우: 명시된 기간을 파티션 조건(year, month, day)으로 변환하여 사용
              (예: year='2026' AND month='02' AND day='01')
            - 사용자가 날짜를 명시하지 않은 경우("최근", "요즘", 날짜 없음):
              Google → year='2026' AND month='02' AND day='01'
              Kakao  → year='2022' AND month='10' AND day='13'
            - CURRENT_DATE 사용 금지. 반드시 위의 실제 데이터 날짜를 파티션 조건으로 사용할 것

            ---

            ### 답변 규칙

            **데이터를 성공적으로 조회했을 때:**
            
            답변을 보내기 전 반드시 생각할 것. 
            1. 마크다운 포맷 사용하여 정리: 정리 제목(##), 굵게(**), 줄바꿈(\\n)
            2. 제목과 소제목 선정 후 적절한 헤딩 붙임.
          

            답변을 보내면서 생각할 것
            1. 사용자 질문에 정확하고 친절하게 답변
            2. 핵심 지표를 강조 (굵게 표현 또는 숫자 강조)
            3. 인사이트 제공 (단순 수치가 아닌 의미 있는 분석)
            4. 제목/소제목 좌우에는 줄바꿈 추가. 
            5. 여러가지를 리스트로 전송 (리스트 항목(-))또는 번호가 붙은 항목 (1., 2., 3.) 좌우에는 줄바꿈 추가.

            **마크다운 헤딩/리스트 규칙 (CRITICAL):**
            - ## 또는 ### 기호 뒤에 반드시 공백 1개를 추가하라 (올바른 예: ## 제목, ### 소제목)
            - ## 또는 ### 기호 뒤에 공백 없이 텍스트를 바로 붙이는 것은 절대 금지 (잘못된 예: ##제목, ###소제목)
            - 헤딩(##, ###) 위와 아래에는 반드시 빈 줄(\\n\\n)을 추가하라.
            - 절대로 헤딩을 본문 끝에 바로 붙이지 말 것 (예: "분석### 헤딩" 금지)
            - ## 섹션 제목 바로 뒤에 번호(1., 2., 3.)나 텍스트를 절대 붙이지 말 것.
              반드시 빈 줄(\\n\\n) 후에 ### 1. 소제목을 시작하라.
              잘못된 예: ## 💡 주요 인사이트1. 비용 효율성
              올바른 예: ## 💡 주요 인사이트\\n\\n### 1. 비용 효율성
            - 리스트 항목은 반드시 '\\n- 내용' 형식으로 작성하라.
              올바른 예: \\n- CTR: 6.65%\\n- 클릭수: 4,692회
              잘못된 예: -CTR: 6.65%-클릭수: 4,692회
              리스트 항목 사이에는 반드시 줄바꿈이 있어야 하며, '-' 뒤에는 반드시 공백 1개를 추가하라.
            - 번호가 있는 모든 항목(1., 2., 3. 등)은 예외 없이 ### 소제목으로 작성하라.
              올바른 예:
                ### 1. 모바일 성과
                ### 2. PC 성과
              잘못된 예: '1. 모바일성과', '2.PC 성과' (### 없이 숫자만)
              반드시 모든 번호 항목에 ### 을 붙여야 하며, 하나라도 빠뜨리면 안 된다.
            - 주요 섹션 사이에 --- 구분선을 추가하여 가독성을 높여라.
              예: 핵심 지표 섹션과 인사이트 섹션 사이, 인사이트와 개선제안 사이
            - 지표를 나열할 때 콜론(:) 뒤에 반드시 공백 1개를 추가하라.
              잘못된 예: 총 클릭수:4,692회
              올바른 예: 총 클릭수: 4,692회

            **예시 답변:**
            ```
            ## 📊 전체 성과 요약\\n\\n- 총 노출수: 70,534회\\n- 총 클릭수: 4,692회\\n- 평균 CTR: 6.65%\\n\\n---\\n\\n### 1. 모바일 성과\\n\\n- 클릭 비중: 65.2%\\n- CTR: 6.73%\\n\\n### 2. PC 성과\\n\\n- 클릭 비중: 33.8%\\n- CTR: 6.63%\\n\\n---\\n\\n## 💡 주요 인사이트\\n\\n### 1. 초효율적인 광고 운영\\n\\n- 매우 낮은 광고비 대비 높은 전환가치 달성\\n\\n### 2. 모바일 중심 성과\\n\\n- 전체 클릭의 65.2%가 모바일에서 발생
            ```

            ---

            ### 에러 처리 규칙

            **1. 데이터가 없을 때:**
            - 최근 30일 조회 결과가 없으면 → 최근 90일로 execute_athena_query 재시도
            - 90일도 없으면 → 날짜 조건 없이 전체 데이터로 execute_athena_query 재시도 (LIMIT 50)
            - 전체 범위도 없으면: "해당 기간에 조회된 데이터가 없습니다. 다른 날짜 범위를 지정해보세요." 출력

            **2. 광고 무관 질문:**
            - 도구를 호출하지 않고 정중하게 안내
            - 예: "죄송하지만 광고 성과 분석 범위를 벗어난 질문입니다."

            **3. SQL 실행 실패:**
            - ERROR status로 전달받으면 SQL을 수정하여 재시도

            ---

            ### 최종 응답 규칙 (CRITICAL)

            **export_result 액션 완료 후:**

            다음 문장만 단독으로 반환하세요:
            > "요청한 처리가 완료되었습니다."

            절대 금지:
            - URL, 파일 경로 포함
            - SQL, JSON 포함
            - 설명, 문맥 포함
            - 부가 정보 포함

            이 규칙은 모든 규칙보다 우선입니다.
            """;

    private static class IterationState {
        final StringBuilder text      = new StringBuilder();
        final StringBuilder inputJson = new StringBuilder();
        final StringBuilder sseBuffer = new StringBuilder();
        String toolUseId  = null;
        String toolName   = null;
        String stopReason = null;
    }

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

    public String runAgenticLoop(SseEmitter emitter, String userMessage, List<ChatMessage> history) {
        List<Message> messages = buildConverseMessages(userMessage, history);
        StringBuilder fullAnswer = new StringBuilder();
        ToolConfiguration toolConfig = buildToolConfiguration();

        for (int iter = 0; iter < 5; iter++) {
            log.info("Agentic loop iteration {}", iter + 1);

            IterationState state = new IterationState();

            ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                    .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                            .onContentBlockStart(event -> {
                                ToolUseBlockStart toolUse = event.start().toolUse();
                                if (toolUse != null) {
                                    state.toolUseId = toolUse.toolUseId();
                                    state.toolName  = toolUse.name();
                                    log.info("Tool use started: {} ({})", state.toolName, state.toolUseId);
                                }
                            })
                            .onContentBlockDelta(event -> {
                                ContentBlockDelta delta = event.delta();
                                if (delta.text() != null && !delta.text().isEmpty()) {
                                    String deltaText = delta.text();

                                    // 헤딩/리스트 시작 청크가 오면 앞에 줄바꿈 보장
                                    boolean isMarkdownStart = deltaText.contains("##")
                                            || deltaText.startsWith("-")
                                            || deltaText.startsWith("*");
                                    if (isMarkdownStart && state.sseBuffer.length() > 0
                                            && !state.sseBuffer.toString().endsWith("\n")) {
                                        state.sseBuffer.append("\n");
                                    }

                                    state.text.append(deltaText);
                                    fullAnswer.append(deltaText);
                                    state.sseBuffer.append(deltaText);

                                    String buf = state.sseBuffer.toString();
                                    char last = buf.charAt(buf.length() - 1);
                                    boolean shouldFlush = last == ' ' || last == '\n'
                                            || last == '.' || last == '!' || last == '?'
                                            || last == ',' || last == '\u3002' // 。
                                            || deltaText.contains("##")
                                            || state.sseBuffer.length() >= 20;
                                    if (shouldFlush) {
                                        try {
                                            String toSend = buf.replaceAll("(#{1,3})([^\\s#])", "$1 $2");
                                            emitter.send(SseEmitter.event().data(toSend, MediaType.TEXT_PLAIN));
                                        } catch (Exception e) {
                                            log.warn("SSE 전송 실패: {}", e.getMessage());
                                        }
                                        state.sseBuffer.setLength(0);
                                    }
                                } else if (delta.toolUse() != null && delta.toolUse().input() != null) {
                                    state.inputJson.append(delta.toolUse().input());
                                }
                            })
                            .onMessageStop(event -> {
                                state.stopReason = event.stopReasonAsString();
                                log.info("stopReason: {}", state.stopReason);
                            })
                            .build())
                    .build();

            try {
                bedrockRuntimeAsyncClient.converseStream(
                        ConverseStreamRequest.builder()
                                .modelId(modelId)
                                .system(SystemContentBlock.fromText(AGENTIC_SYSTEM_PROMPT))
                                .messages(messages)
                                .toolConfig(toolConfig)
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

            // 스트림 종료 후 버퍼에 남은 내용 flush
            if (state.sseBuffer.length() > 0) {
                try {
                    String toSend = state.sseBuffer.toString().replaceAll("(#{1,3})([^\\s#])", "$1 $2");
                    emitter.send(SseEmitter.event().data(toSend, MediaType.TEXT_PLAIN));
                } catch (Exception e) {
                    log.warn("SSE 잔여 버퍼 전송 실패: {}", e.getMessage());
                }
                state.sseBuffer.setLength(0);
            }

            // Parse SQL from accumulated tool input JSON
            String sql = null;
            if (state.toolUseId != null && state.inputJson.length() > 0) {
                try {
                    sql = objectMapper.readTree(state.inputJson.toString()).get("sql").asText();
                } catch (Exception e) {
                    log.error("SQL 파싱 실패: {}", e.getMessage());
                }
            }

            // Reconstruct assistant message and add to history
            List<ContentBlock> assistantContent = new ArrayList<>();
            if (state.text.length() > 0) {
                assistantContent.add(ContentBlock.fromText(state.text.toString()));
            }
            if (state.toolUseId != null && sql != null) {
                assistantContent.add(ContentBlock.fromToolUse(
                        ToolUseBlock.builder()
                                .toolUseId(state.toolUseId)
                                .name(state.toolName)
                                .input(Document.mapBuilder().putString("sql", sql).build())
                                .build()
                ));
            }
            if (!assistantContent.isEmpty()) {
                messages.add(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(assistantContent)
                        .build());
            }

            // Decide next step
            if (!"tool_use".equals(state.stopReason) || state.toolUseId == null || sql == null) {
                break;
            }

            // Execute Athena query and feed result back to Claude
            String toolResultContent;
            ToolResultStatus toolResultStatus;
            try {
                AthenaService.AthenaResult athenaResult = athenaService.executeQuery(sql);
                toolResultContent = athenaResult.text();
                toolResultStatus  = ToolResultStatus.SUCCESS;
                log.info("Athena 쿼리 성공");

                try {
                    emitter.send(SseEmitter.event()
                            .name("data")
                            .data(athenaResult.json(), MediaType.APPLICATION_JSON));
                } catch (Exception e) {
                    log.warn("데이터 SSE 전송 실패: {}", e.getMessage());
                }
            } catch (Exception e) {
                toolResultContent = "쿼리 실행 실패: " + e.getMessage();
                toolResultStatus  = ToolResultStatus.ERROR;
                log.error("Athena 쿼리 실패: {}", e.getMessage());
            }

            messages.add(Message.builder()
                    .role(ConversationRole.USER)
                    .content(ContentBlock.fromToolResult(
                            ToolResultBlock.builder()
                                    .toolUseId(state.toolUseId)
                                    .status(toolResultStatus)
                                    .content(ToolResultContentBlock.fromText(toolResultContent))
                                    .build()
                    ))
                    .build());
        }

        return fullAnswer.toString();
    }

    private ToolConfiguration buildToolConfiguration() {
        Document inputSchema = Document.mapBuilder()
                .putString("type", "object")
                .putString("description", "Athena SQL 쿼리를 실행하여 광고 데이터를 조회합니다")
                .putDocument("properties", Document.mapBuilder()
                        .putDocument("sql", Document.mapBuilder()
                                .putString("type", "string")
                                .putString("description", "실행할 Athena SQL 쿼리")
                                .build())
                        .build())
                .putDocument("required", Document.listBuilder()
                        .addString("sql")
                        .build())
                .build();

        return ToolConfiguration.builder()
                .tools(Tool.fromToolSpec(
                        ToolSpecification.builder()
                                .name("execute_athena_query")
                                .description("AWS Athena를 사용하여 광고 성과 데이터를 조회합니다. " +
                                           "제공된 SQL을 실행하고 결과를 반환합니다.")
                                .inputSchema(ToolInputSchema.fromJson(inputSchema))
                                .build()
                ))
                .build();
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
