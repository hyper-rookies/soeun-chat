package nhnad.soeun_chat.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import nhnad.soeun_chat.domain.chat.dto.ChatRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Chat", description = "AI 광고 성과 분석 채팅")
@RequestMapping("/api/chat")
public interface ChatApi {

    @Operation(
            summary = "채팅 메시지 전송 (SSE 스트리밍)",
            description = """
                    사용자 메시지를 전송하면 AI가 광고 성과 데이터를 분석하여 **SSE(Server-Sent Events)** 방식으로 실시간 스트리밍 응답합니다.

                    **응답 Content-Type:** `text/event-stream`

                    **SSE 이벤트 형식:**
                    ```
                    data: {"type":"text","content":"이번 주"}
                    data: {"type":"text","content":" 광고 비용은"}
                    data: {"type":"chart","chartType":"bar","data":[{"date":"2024-01-01","cost":150000}]}
                    data: [DONE]
                    ```

                    **이벤트 타입 (`type` 필드)**
                    | type | 설명 |
                    |---|---|
                    | `text` | 텍스트 청크 — `content` 필드를 순서대로 이어붙여 표시 |
                    | `chart` | 차트 데이터 — `chartType`(bar/line/pie)과 `data` 배열 포함 |
                    | `table` | 테이블 데이터 — `data` 배열에 행 데이터 포함 |
                    | `[DONE]` | 스트리밍 종료 시그널 — SSE 연결을 닫아도 됩니다 |

                    **클라이언트 구현 예시:**
                    ```js
                    // POST SSE는 fetch + ReadableStream으로 처리
                    const res = await fetch(`/api/chat/${conversationId}`, {
                      method: 'POST',
                      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
                      body: JSON.stringify({ message })
                    });
                    const reader = res.body.getReader();
                    // 청크를 읽어 파싱
                    ```

                    > ⚠️ SSE POST 방식이므로 Swagger UI에서 직접 테스트가 불가합니다. Postman(SSE 지원) 또는 직접 구현으로 테스트하세요.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "스트리밍 시작 성공",
                    content = @Content(
                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            examples = @ExampleObject(
                                    name = "SSE 스트림 예시",
                                    value = """
                                            data: {"type":"text","content":"이번 주 카카오 광고 비용은 "}

                                            data: {"type":"text","content":"1,200,000원입니다."}

                                            data: {"type":"chart","chartType":"bar","data":[{"date":"2024-01-01","cost":150000}]}

                                            data: [DONE]
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 — Authorization 헤더 누락 또는 만료된 토큰",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"INVALID_TOKEN\",\"message\":\"유효하지 않은 토큰입니다.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "대화를 찾을 수 없음 — conversationId 가 존재하지 않거나 본인 대화가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"NOT_FOUND\",\"message\":\"대화를 찾을 수 없습니다.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류 (Bedrock / Athena 연동 실패 등)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 오류가 발생했습니다.\"}")
                    )
            )
    })
    @PostMapping(value = "/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chat(
            @Parameter(description = "대화 ID — `POST /api/conversations` 로 생성한 값", example = "conv-abc123")
            @PathVariable String conversationId,
            @RequestBody ChatRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal String userId
    );
}
