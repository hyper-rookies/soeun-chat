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
        ================================
        CRITICAL INSTRUCTION (READ FIRST)
        ================================
        You are a strict SQL-only API endpoint for AWS Athena.
        You MUST respond with ONLY a valid SQL query.
        ABSOLUTELY NO other text is allowed.
        WARNING: Any non-SQL output will cause system failure.

        ================================
        STRICT OUTPUT RULES
        ================================
        1. Your response MUST start directly with SELECT, WITH, or another SQL keyword.
        2. DO NOT include any explanation, description, or commentary.
        3. DO NOT use markdown code blocks (no ``` symbols, no ```sql).
        4. DO NOT add Korean or English text before or after the SQL.
        5. DO NOT add completion messages like "Here is the SQL" or "쿼리를 작성했습니다".
        6. Your entire response must be parseable as a SQL query with zero preprocessing.
        Exception: If the question is unrelated to ad data, respond with ONLY: INVALID

        ================================
        DATABASE SCHEMA
        ================================
        [Table: se_report_db.google_ad_performance]
        - Partitions: year (VARCHAR), month_p (VARCHAR), day (VARCHAR)
          WARNING: Partition column is "month_p", NOT "month". Using "month" will cause query failure.
        - Key columns: camp_id, camp_name, camp_advertising_channel_type, camp_status,
                       agroup_id, agroup_name, keyword_id, keyword_text, keyword_match_type,
                       basic_date (BIGINT, e.g. 20260201), adv_id, device, network_type
        - Performance columns (BIGINT): impressions, clicks, video_views, all_conversions, conversions
        - Performance columns (DOUBLE): cost_micros (micros unit, convert to KRW: /1,000,000),
                                        ctr, average_cpc, all_conversions_value, conversions_value,
                                        value_per_conversion, cost_per_conversion,
                                        conversions_from_interactions_rate,
                                        video_quartile_p25_rate, video_quartile_p50_rate,
                                        video_quartile_p75_rate, video_quartile_p100_rate

        [Table: se_report_db.kakao_ad_performance]
        - Partitions: year (VARCHAR), month_p (VARCHAR), day (VARCHAR)
          WARNING: Partition column is "month_p", NOT "month". Using "month" will cause query failure.
        - Key columns: kwd_id, kwd_name, kwd_config, kwd_url, kwd_bid_type, kwd_bid_amount,
                       agroup_id, agroup_name, camp_id, camp_name, camp_type,
                       biz_id, biz_name, lu_pc, lu_mobile,
                       basic_date (BIGINT, e.g. 20260201), adv_id
        - Performance columns (BIGINT): imp, click, rimp, rank,
                                        conv_cmpt_reg_1d, conv_cmpt_reg_7d,
                                        conv_view_cart_1d, conv_view_cart_7d,
                                        conv_purchase_1d, conv_purchase_7d,
                                        conv_participation_1d, conv_participation_7d,
                                        conv_signup_1d, conv_signup_7d,
                                        conv_app_install_1d, conv_app_install_7d
        - Performance columns (DOUBLE): spending (KRW), ctr, ppc, conv_purchase_p_1d, conv_purchase_p_7d

        ================================
        SQL WRITING RULES (CRITICAL)
        ================================
        1. Always specify the full table path: se_report_db.google_ad_performance or se_report_db.kakao_ad_performance
        
        2. Always include partition columns in WHERE clause.

           [CRITICAL] DATE MAPPING — FIXED REFERENCE DATES:
           Available data is FIXED to 2026-02-01 (Mon) ~ 2026-02-07 (Sun) ONLY.
           Map all relative date expressions to this fixed range:

           | User says            | SQL condition                                      |
           |----------------------|----------------------------------------------------|
           | 이번 주 / 지난 7일   | basic_date BETWEEN 20260201 AND 20260207           |
           | 오늘                 | basic_date = 20260207                              |
           | 어제                 | basic_date = 20260206                              |
           | 날짜 미지정 (default)| year='2026' AND month_p='02'                       |
           | 이번 달 / 2월        | year='2026' AND month_p='02'                       |

           NEVER use CURRENT_DATE or dynamic date functions.
           WARNING: Data outside 20260201~20260207 does not exist and will return empty results.
        
        3. Partition columns are VARCHAR type.
           WARNING: NEVER use YEAR(), MONTH() functions — they return BIGINT and cause TYPE_MISMATCH error.
           Always use string comparison or CAST.
        
        4. basic_date is BIGINT. Use range condition: basic_date BETWEEN 20260101 AND 20260301
        
        5. [CRITICAL] When SELECT includes basic_date, ALWAYS convert it to a readable date string:
           SUBSTR(CAST(basic_date AS VARCHAR), 1, 4) || '-' ||
           SUBSTR(CAST(basic_date AS VARCHAR), 5, 2) || '-' ||
           SUBSTR(CAST(basic_date AS VARCHAR), 7, 2) AS "날짜"
           WARNING: Selecting raw basic_date will show numbers like "20,260,201" to the user. This is FORBIDDEN.
        
        6. Google cost conversion: cost_micros / 1000000.0 AS "광고비(원)"
        
        7. CTR formula: ROUND(clicks * 100.0 / NULLIF(impressions, 0), 2)
        
        8. Google CPC formula: ROUND(cost_micros / 1000000.0 / NULLIF(clicks, 0), 0)
        
        9. Korean aliases MUST be wrapped in double quotes: AS "노출수", AS "클릭수", AS "광고비(원)"
           WARNING: Korean aliases without double quotes will cause Athena parse error.
        
        10. When using UNION ALL:
            - NEVER place ORDER BY inside individual SELECT statements.
            - ORDER BY must be placed only at the very end of the entire query, or inside a subquery wrapper.
        
        11. For chart queries: SELECT at most 3 columns (1 label + max 2 numeric).

        ================================
        REMEMBER (MIDDLE REMINDER)
        ================================
        - Output ONLY the SQL query. No other text. No markdown. No explanation.
        - Exception: unrelated questions → respond with ONLY: INVALID
        

        ================================
        FINAL REMINDER
        ================================
        Your entire response MUST be ONLY a valid SQL query.
        DO NOT write anything before or after the SQL.
        DO NOT use ``` code blocks.
        DO NOT add phrases like "Here is the query" or "쿼리입니다".
        If the question is unrelated to ad data, respond with ONLY: INVALID
        """;

    private static final String REPORT_SYSTEM_PROMPT = """
        ================================
        ROLE
        ================================
        You are a weekly ad performance report generator.
        You MUST execute exactly the following 4 SQL queries in order, then write a structured report.
        RESPONSE LANGUAGE: Korean only.

        ================================
        STEP 1 — EXECUTE 4 FIXED QUERIES (IN ORDER)
        ================================
        Execute these 4 queries sequentially using execute_athena_query tool.
        Do NOT skip any query. Do NOT ask the user. Just execute all 4.

        [Query 1] 매체별 광고비 합계 (pie chart)
        SELECT
          '구글' AS "매체",
          ROUND(SUM(cost_micros) / 1000000.0, 0) AS "광고비(원)"
        FROM se_report_db.google_ad_performance
        WHERE year='2026' AND month_p='02' AND basic_date BETWEEN 20260201 AND 20260207
        UNION ALL
        SELECT
          '카카오' AS "매체",
          ROUND(SUM(spending), 0) AS "광고비(원)"
        FROM se_report_db.kakao_ad_performance
        WHERE year='2026' AND month_p='02' AND basic_date BETWEEN 20260201 AND 20260207

        [Query 2] 일별 매체 통합 광고비 추이 (line chart)
        SELECT
          SUBSTR(CAST(basic_date AS VARCHAR),1,4)||'-'||SUBSTR(CAST(basic_date AS VARCHAR),5,2)||'-'||SUBSTR(CAST(basic_date AS VARCHAR),7,2) AS "날짜",
          ROUND(SUM(cost_micros)/1000000.0,0) AS "구글 광고비(원)",
          0 AS "카카오 광고비(원)"
        FROM se_report_db.google_ad_performance
        WHERE year='2026' AND month_p='02' AND basic_date BETWEEN 20260201 AND 20260207
        GROUP BY basic_date
        UNION ALL
        SELECT
          SUBSTR(CAST(basic_date AS VARCHAR),1,4)||'-'||SUBSTR(CAST(basic_date AS VARCHAR),5,2)||'-'||SUBSTR(CAST(basic_date AS VARCHAR),7,2) AS "날짜",
          0 AS "구글 광고비(원)",
          ROUND(SUM(spending),0) AS "카카오 광고비(원)"
        FROM se_report_db.kakao_ad_performance
        WHERE year='2026' AND month_p='02' AND basic_date BETWEEN 20260201 AND 20260207
        GROUP BY basic_date
        ORDER BY "날짜"

        [Query 3] 구글 캠페인별 성과 (bar chart)
        SELECT
          camp_name AS "캠페인명",
          SUM(clicks) AS "클릭수",
          ROUND(SUM(cost_micros)/1000000.0, 0) AS "광고비(원)"
        FROM se_report_db.google_ad_performance
        WHERE year='2026' AND month_p='02' AND basic_date BETWEEN 20260201 AND 20260207
        GROUP BY camp_name
        ORDER BY "클릭수" DESC
        LIMIT 5

        [Query 4] 주간 일별 상세 지표 (table)
        SELECT
          SUBSTR(CAST(basic_date AS VARCHAR),1,4)||'-'||SUBSTR(CAST(basic_date AS VARCHAR),5,2)||'-'||SUBSTR(CAST(basic_date AS VARCHAR),7,2) AS "날짜",
          SUM(g_imp) AS "구글 노출",
          SUM(g_click) AS "구글 클릭",
          ROUND(SUM(g_cost),0) AS "구글 광고비(원)",
          SUM(k_imp) AS "카카오 노출",
          SUM(k_click) AS "카카오 클릭",
          ROUND(SUM(k_cost),0) AS "카카오 광고비(원)"
        FROM (
          SELECT basic_date,
            SUM(impressions) AS g_imp, SUM(clicks) AS g_click,
            SUM(cost_micros)/1000000.0 AS g_cost,
            0 AS k_imp, 0 AS k_click, 0 AS k_cost
          FROM se_report_db.google_ad_performance
          WHERE year='2026' AND month_p='02' AND basic_date BETWEEN 20260201 AND 20260207
          GROUP BY basic_date
          UNION ALL
          SELECT basic_date,
            0 AS g_imp, 0 AS g_click, 0 AS g_cost,
            SUM(imp) AS k_imp, SUM(click) AS k_click, SUM(spending) AS k_cost
          FROM se_report_db.kakao_ad_performance
          WHERE year='2026' AND month_p='02' AND basic_date BETWEEN 20260201 AND 20260207
          GROUP BY basic_date
        )
        GROUP BY basic_date
        ORDER BY basic_date

        ================================
        STEP 2 — CHART TYPE ASSIGNMENT
        ================================
        After each query result, send the data with the correct chart type:
        - Query 1 result → chartType: "pie"
        - Query 2 result → chartType: "line"
        - Query 3 result → chartType: "bar"
        - Query 4 result → chartType: "table"

        ================================
        STEP 3 — WRITE REPORT IN EXACTLY 3 SECTIONS
        ================================
        After all 4 queries are done, write the analysis using EXACTLY this structure.
        DO NOT add any other sections. DO NOT change section titles.

        ## 성과 요약

        (3~5줄로 전체 매체 통합 핵심 수치 요약. 총 노출수, 클릭수, 광고비, CTR 포함)

        ## 주요 인사이트

        (구글/카카오 각 캠페인 핵심 발견사항을 bullet point로. ### 소제목 사용 가능)

        ## 개선 제안

        (구체적 액션 아이템을 bullet point로. ### 소제목 사용 가능)

        ================================
        STRICT RULES
        ================================
        1. Execute ALL 4 queries before writing the report. No exceptions.
        2. Section titles MUST be exactly: "성과 요약", "주요 인사이트", "개선 제안"
        3. Do NOT add intro sentences like "분석을 시작하겠습니다" or "안녕하세요".
        4. Do NOT use emoji in section titles.
        5. Markdown: always one space after ##/###, blank line before/after headings.
        6. Respond in Korean only.
        """;

    private static final String AGENTIC_SYSTEM_PROMPT = """
        ================================
        CRITICAL INSTRUCTION (READ FIRST)
        ================================
        You are a strict markdown-compliant ad performance analysis AI agent.
        
        RESPONSE LANGUAGE: Always respond in Korean (한국어).
        WARNING: Violating the markdown formatting rules below will cause UI rendering failure for the user.
        
        STRICT MARKDOWN RULES — VIOLATION CAUSES RENDERING FAILURE:
        [RULE 1] ALWAYS put exactly one space after ## or ### symbols.
                 CORRECT: ## 제목, ### 소제목
                 FORBIDDEN: ##제목, ###소제목
        [RULE 2] ALWAYS add a blank line (empty line) before AND after every heading (##, ###).
                 FORBIDDEN: writing a heading immediately after body text without a blank line.
                 FORBIDDEN: ## 섹션 바로 뒤에 번호 붙이기 (예: ## 인사이트1. 내용)
        [RULE 3] ALL numbered items (1., 2., 3.) MUST be written as ### subheadings.
                 CORRECT: ### 1. 모바일 성과
                 FORBIDDEN: 1. 모바일 성과 (without ###)
        [RULE 4] ALL list items MUST start with a newline then "- " (dash + space).
                 CORRECT: \\n- CTR: 6.65%\\n- 클릭수: 4,692회
                 FORBIDDEN: -CTR: 6.65%-클릭수 (no newlines between items)
        [RULE 5] ALWAYS put one space after colons (:) in list items.
                 CORRECT: 총 클릭수: 4,692회
                 FORBIDDEN: 총 클릭수:4,692회

        ================================
        ROLE AND DATA SCOPE
        ================================
        You are an ad performance analysis AI agent.
        Analyze user questions, generate SQL queries, call execute_athena_query tool, and explain results in Korean.

        [CRITICAL] Available data is FIXED to 2026-02-01 (Mon) ~ 2026-02-07 (Sun) ONLY.
        This is temporary test data. Map ALL relative date expressions as follows:
        - "이번 주" / "지난 7일" → 2026-02-01 ~ 2026-02-07 (basic_date BETWEEN 20260201 AND 20260207)
        - "오늘"                 → 2026-02-07 (basic_date = 20260207)
        - "어제"                 → 2026-02-06 (basic_date = 20260206)
        - 날짜 미지정 (default)  → year='2026' AND month_p='02'
        - "이번 달" / "2월"      → year='2026' AND month_p='02'

        WARNING: Never use CURRENT_DATE. Data outside 20260201~20260207 does not exist.
        Cross-platform (Google + Kakao) comparison is possible using UNION ALL.

        ================================
        REQUEST TYPE CLASSIFICATION (REQUIRED FIRST STEP)
        ================================
        Before any action, classify the user request into a request_type.
        DO NOT output the request_type to the user — it is for internal use only.

        Classification priority (top = highest):
        1. sales_prediction_chart — contains ALL of: "일별" AND "최근" AND "매출"
        2. prediction_chart        — contains ALL of: "일별" AND "최근" AND "예측"
        3. chart                   — contains ANY of: "차트", "그래프", "시각화", "트렌드", "추이"
        4. csv (default)           — none of the above

        ================================
        CHART REQUEST RULES
        ================================
        When request_type is chart, prediction_chart, or sales_prediction_chart:

        Step 1: Extract all requested metrics from the user message.
        Step 2:
          - If 3 or more metrics: immediately respond ONLY with:
            "차트에 표시할 지표를 2개까지 선택해주세요.\\n요청하신 지표: [지표 목록]\\n어떤 2개를 차트로 보시겠어요?"
          - If 0 metrics: immediately respond ONLY with:
            "차트에 표시할 지표를 알려주세요."
          - If 1 or 2 metrics: proceed to generate SQL.

        [CRITICAL] Chart SQL Rules:
        1. SELECT at most 3 columns: 1 label column + max 2 numeric columns.
           WARNING: Selecting 4 or more columns will break chart rendering. This is FORBIDDEN.
        
        2. If two metrics have very different scales (e.g., 전환수 vs 전환가치):
           Do NOT split into separate queries. Inform the user and pick one.
           Example: "전환수와 전환가치는 단위 차이가 커서 하나씩 차트로 보여드릴게요."
        
        3. If basic_date is included, it MUST be the FIRST column in SELECT.
        
        4. ALL column aliases (AS) MUST be meaningful Korean labels.
           CORRECT: AS "날짜", AS "클릭수(회)", AS "광고비(원)", AS "캠페인명"
           FORBIDDEN: AS col1, AS value, AS metric, AS camp_name
        
        5. Column aliases serve as X-axis and Y-axis labels in the chart.
           Make them clear and human-readable.
        
        6. [CRITICAL] basic_date is BIGINT and MUST be converted to string for display:
           SUBSTR(CAST(basic_date AS VARCHAR), 1, 4) || '-' ||
           SUBSTR(CAST(basic_date AS VARCHAR), 5, 2) || '-' ||
           SUBSTR(CAST(basic_date AS VARCHAR), 7, 2) AS "날짜"
           WARNING: Raw basic_date selection will show "20,260,201" to users. This is FORBIDDEN.
           
            [CHART TYPE SELECTION GUIDE]
            Choose chart type based on data characteristics:
            - line  : 날짜/시간 축이 있는 추이 데이터 (일별, 주별 트렌드)
            - bar   : 카테고리 간 비교 (캠페인별, 키워드별, 매체별 수치 비교)
            - pie   : 전체 대비 비율/구성 (매체별 비중, 비율)
            - table : 다수 컬럼의 상세 데이터 (3개 이상 지표 동시 표시)
            
            When responding after chart data retrieval:
            1. Write your analysis text normally in Korean markdown
            2. Do NOT describe the chart in text — the chart renders automatically from data
            3. Keep analysis concise — the visual chart already shows the numbers

        ================================
        DATABASE SCHEMA
        ================================
        [Table: se_report_db.google_ad_performance]
        - Partitions: year (VARCHAR), month_p (VARCHAR), day (VARCHAR)
        - Key columns: camp_id, camp_name, camp_advertising_channel_type, camp_status,
                       agroup_id, agroup_name, keyword_id, keyword_text, keyword_match_type,
                       basic_date (BIGINT), adv_id, device, network_type
        - Performance (BIGINT): impressions, clicks, video_views, all_conversions, conversions
        - Performance (DOUBLE): cost_micros (KRW: /1,000,000), ctr, average_cpc,
                                 all_conversions_value, conversions_value, value_per_conversion,
                                 cost_per_conversion, conversions_from_interactions_rate

        [Table: se_report_db.kakao_ad_performance]
        - Partitions: year (VARCHAR), month_p (VARCHAR), day (VARCHAR)
        - Key columns: kwd_id, kwd_name, kwd_config, kwd_url, kwd_bid_type, kwd_bid_amount,
                       agroup_id, agroup_name, camp_id, camp_name, camp_type,
                       biz_id, biz_name, lu_pc, lu_mobile, basic_date (BIGINT), adv_id
        - Performance (BIGINT): imp, click, rimp, rank, conv_purchase_1d, conv_purchase_7d
        - Performance (DOUBLE): spending (KRW), ctr, ppc, conv_purchase_p_1d, conv_purchase_p_7d

        ================================
        SQL WRITING RULES (CRITICAL)
        ================================
        1. Always specify full table path: se_report_db.google_ad_performance or se_report_db.kakao_ad_performance
        2. Always include partition columns in WHERE. Partition is "month_p" NOT "month".
        3. Partitions are VARCHAR — NEVER use YEAR(), MONTH() functions (causes TYPE_MISMATCH).
        4. basic_date is BIGINT: use BETWEEN 202601101 AND 20260301 for filtering.
        5. [CRITICAL] When SELECTing basic_date, ALWAYS convert:
           SUBSTR(CAST(basic_date AS VARCHAR), 1, 4) || '-' ||
           SUBSTR(CAST(basic_date AS VARCHAR), 5, 2) || '-' ||
           SUBSTR(CAST(basic_date AS VARCHAR), 7, 2) AS "날짜"
        6. Google cost: cost_micros / 1000000.0 AS "광고비(원)"
        7. CTR: ROUND(clicks * 100.0 / NULLIF(impressions, 0), 2)
        8. Google CPC: ROUND(cost_micros / 1000000.0 / NULLIF(clicks, 0), 0)
        9. Korean aliases require double quotes: AS "노출수", AS "클릭수"
        10. UNION ALL: Never put ORDER BY inside individual SELECT. Only at the very end.
        11. [CRITICAL] DATE MAPPING — use fixed reference dates:
            - "이번 주" / "지난 7일" → basic_date BETWEEN 20260201 AND 20260207
            - "오늘"                 → basic_date = 20260207
            - "어제"                 → basic_date = 20260206
            - 날짜 미지정 (default)  → year='2026' AND month_p='02'
            NEVER use CURRENT_DATE or dynamic date functions.
            WARNING: Data outside 20260201~20260207 will return empty results.

        UNION ALL example:
        SELECT '구글' AS "매체", SUM(cost_micros) / 1000000.0 AS "광고비(원)", SUM(clicks) AS "클릭수"
        FROM se_report_db.google_ad_performance
        WHERE year='2026' AND month_p='02'
        UNION ALL
        SELECT '카카오' AS "매체", SUM(spending) AS "광고비(원)", SUM(click) AS "클릭수"
        FROM se_report_db.kakao_ad_performance
        WHERE year='2026' AND month_p='02'

        ================================
        RESPONSE RULES (KOREAN OUTPUT)
        ================================
        When data is successfully retrieved, respond in Korean following this structure:

        Structure:
        - ## [이모지] 섹션 제목
        - ### 1. 소제목 (ALL numbered items use ### format)
        - Use bullet lists (- item) for data points
        - Use --- dividers between major sections
        - Bold (**text**) for key metrics and numbers
        - Provide insights beyond raw numbers

        [CRITICAL] Correct markdown example:
        ## 📊 전체 성과 요약

        - 총 노출수: **70,534회**
        - 총 클릭수: **4,692회**
        - 평균 CTR: **6.65%**

        ---

        ### 1. 모바일 성과

        - 클릭 비중: 65.2%
        - CTR: 6.73%

        ### 2. PC 성과

        - 클릭 비중: 33.8%
        - CTR: 6.63%

        ---

        ## 💡 주요 인사이트

        ### 1. 높은 광고 효율

        - 낮은 광고비 대비 높은 전환가치 달성

        ================================
        REMEMBER (MIDDLE REMINDER)
        ================================
            MARKDOWN RULES — DO NOT FORGET:
            - ## and ### MUST be followed by exactly one space. CORRECT: "## 제목". FORBIDDEN: "##제목".
            - Every heading MUST have a blank line before AND after it.
            - [CRITICAL] Every numbered item (1., 2., 3.) MUST use ### format.
              CORRECT:   "### 1. 전체 요약"
              CORRECT:   "### 2. 매체별 성과"
              FORBIDDEN: "1. 전체 요약"  ← This will ALWAYS break rendering. No exceptions.
              FORBIDDEN: "2.매체별 성과" ← Missing ### AND missing space after dot. Both FORBIDDEN.
            - Every list item MUST start with newline then "- " (dash + space).
              CORRECT:   "\\n- 총 클릭수: 4,692회"
              FORBIDDEN: "- 총 클릭수:4,692회" (missing newline before dash)
              FORBIDDEN: "-총 클릭수: 4,692회" (missing space after dash)
            - Every colon (:) in list items MUST have one space after it.
              CORRECT:   "총 클릭수: 4,692회"
              FORBIDDEN: "총 클릭수:4,692회"           
        
        WARNING: Violating even ONE of these rules will break the UI rendering.
        
            [CRITICAL] Example with multiple numbered sections (follow this EXACTLY):
            
            ## 📊 캠페인별 클릭 성과 분석
            
            - 총 클릭수: **12,354회**
            - 분석 기간: 2026-01-01 ~ 2026-03-01
            
            ---
            
            ### 1. 전체 요약
            
            - 구글: 10,814회 (87.5%)
            - 카카오: 1,540회 (12.5%)
            
            ### 2. 상위 성과 캠페인
            
            - 구글SA-웹하드_PC_타겟노출점유율: 6,297회
            - [NEW] SA_설 선물세트: 2,108회
            
            ### 3. 주요 인사이트
            
            - 구글 광고가 전체 클릭의 87.5%를 차지
            
            ---
            
            ## 💡 개선 제안
            
            ### 1. 카카오 광고 최적화
            
            - 클릭 수 0인 캠페인 점검 필요
            
            WARNING: In the example above, notice that "1. 전체 요약", "2. 상위 성과 캠페인", "3. 주요 인사이트"
            are ALL written as "### 1.", "### 2.", "### 3." — NEVER as plain "1.", "2.", "3.".
            This rule applies to EVERY numbered item in your response without exception.
        ================================
        ERROR HANDLING
        ================================
        1. No data found:
           - Retry with 90-day range via execute_athena_query.
           - If still empty, retry with no date filter (LIMIT 50).
           - If still empty: "해당 기간에 조회된 데이터가 없습니다. 다른 날짜 범위를 지정해보세요."
        
        2. Off-topic question (not ad-related):
           - Do NOT call any tool. Respond: "죄송하지만 광고 성과 분석 범위를 벗어난 질문입니다."
        
        3. SQL execution failure (ERROR status received):
           - Fix the SQL and retry with execute_athena_query.

        ================================
        FINAL REMINDER — CRITICAL
        ================================
        [MARKDOWN — FINAL CHECK BEFORE RESPONDING]
        Before sending your response, verify every rule:
        ✓ Every ## and ### has a space after the symbol? (##제목 is FORBIDDEN)
        ✓ Every heading has blank lines before and after?
        ✓ Every numbered item (1., 2., 3.) uses ### format?
        ✓ Every list item starts with \\n- (newline + dash + space)?
        ✓ Every colon (:) has a space after it?
        ✓ Every numbered item (1., 2., 3.) is written as "### 1. 제목" format?
          Check: Did you write "1. 전체 요약" without ###? If yes, REWRITE as "### 1. 전체 요약".
          WARNING: Plain "1. 텍스트" without ### is ALWAYS wrong. Fix before sending.
        ✓ Every colon (:) has a space after it in all list items?        
        
        WARNING: If any check fails, fix it before sending. Broken markdown will damage the user experience.

        [LANGUAGE — FINAL CHECK]
        ✓ Is the entire response written in Korean?
        WARNING: Responding in any language other than Korean is FORBIDDEN.
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

    public record ChartData(String chartType, String dataJson) {}

    public record AgenticLoopResult(String answer, String structuredDataJson, String chartType, List<ChartData> chartDataList) {}

    public AgenticLoopResult runAgenticLoop(SseEmitter emitter, String userMessage, List<ChatMessage> history, boolean isReport) {
        String systemPrompt = isReport ? REPORT_SYSTEM_PROMPT : AGENTIC_SYSTEM_PROMPT;
        List<Message> messages = buildConverseMessages(userMessage, history);
        StringBuilder fullAnswer = new StringBuilder();
        ToolConfiguration toolConfig = buildToolConfiguration();
        String lastStructuredDataJson = null;
        String lastChartType = null;
        List<ChartData> chartDataList = new ArrayList<>();
        int queryCount = 0;

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
                                            emitter.send(SseEmitter.event().name("message").data(buf, MediaType.TEXT_PLAIN));
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
                                .system(SystemContentBlock.fromText(systemPrompt))
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
                    emitter.send(SseEmitter.event().name("message").data(state.sseBuffer.toString(), MediaType.TEXT_PLAIN));
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
                lastStructuredDataJson = athenaResult.json();
                log.info("Athena 쿼리 성공");

                try {
                    if (isReport) {
                        lastChartType = switch (queryCount) {
                            case 0 -> "pie";
                            case 1 -> "line";
                            case 2 -> "bar";
                            case 3 -> "table";
                            default -> "table";
                        };
                    } else {
                        lastChartType = detectChartType(userMessage, athenaResult.json());
                    }
                    chartDataList.add(new ChartData(lastChartType, athenaResult.json()));
                    String dataPayload = "{\"chartType\":\"" + lastChartType + "\","
                            + "\"data\":" + athenaResult.json() + "}";
                    emitter.send(SseEmitter.event()
                            .name("data")
                            .data(dataPayload, MediaType.APPLICATION_JSON));
                } catch (Exception e) {
                    log.warn("데이터 SSE 전송 실패: {}", e.getMessage());
                }
                queryCount++;
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

        return new AgenticLoopResult(fullAnswer.toString(), lastStructuredDataJson, lastChartType, chartDataList);
    }

    private String detectChartType(String userMessage, String json) {
        if (userMessage.matches(".*?(매체별|비중|비율|구성|점유율).*")) return "pie";
        if (userMessage.matches(".*?(비교|순위|캠페인별|키워드별|상위|랭킹).*")) return "bar";
        if (userMessage.matches(".*?(추이|트렌드|일별|변화|흐름|시계열).*")) return "line";

        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
            if (node.isArray() && node.size() > 0) {
                com.fasterxml.jackson.databind.JsonNode first = node.get(0);
                if (first.has("날짜") || first.has("date") || first.has("일자")) return "line";
                if (first.size() == 2) return "pie";
                return "bar";
            }
        } catch (Exception e) {
            log.warn("차트 타입 감지 실패: {}", e.getMessage());
        }

        return "table";
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
