package nhnad.soeun_chat.domain.conversation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import nhnad.soeun_chat.domain.conversation.dto.ConversationResponse;
import nhnad.soeun_chat.domain.conversation.dto.ConversationSummary;
import nhnad.soeun_chat.domain.conversation.dto.CreateConversationRequest;
import nhnad.soeun_chat.domain.conversation.dto.UpdateTitleRequest;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Conversation", description = "대화(채팅방) 관리 — 채팅 시작 전 반드시 대화를 먼저 생성하세요.")
@RequestMapping("/api/conversations")
public interface ConversationApi {

    @Operation(
            summary = "새 대화 생성",
            description = """
                    새 채팅방을 생성합니다.

                    **사용 순서:**
                    1. 이 API로 대화 생성 → `conversationId` 획득
                    2. `POST /api/chat/{conversationId}` 로 메시지 전송

                    `title`을 비워두면 서버에서 기본 제목으로 저장됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConversationResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "conversationId": "conv-abc123",
                                        "title": "새 대화",
                                        "createdAt": 1709740800000,
                                        "updatedAt": 1709740800000,
                                        "messages": []
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}")))
    })
    @PostMapping
    ApiResponse<ConversationResponse> create(
            @RequestBody CreateConversationRequest request,
            @Parameter(hidden = true) String userId
    );

    @Operation(
            summary = "대화 목록 조회",
            description = "내 대화 목록을 **최신순**으로 반환합니다. 사이드바 목록 렌더링에 사용하세요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ConversationSummary.class)),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "conversationId": "conv-abc123",
                                          "title": "이번 주 광고 성과 분석",
                                          "updatedAt": 1709740800000
                                        }
                                      ]
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}")))
    })
    @GetMapping
    ApiResponse<List<ConversationSummary>> list(
            @Parameter(hidden = true) String userId
    );

    @Operation(
            summary = "대화 상세 조회",
            description = """
                    특정 대화의 **전체 메시지 목록**을 반환합니다.

                    채팅 화면에 진입할 때 이전 대화 내용을 불러오는 데 사용하세요.

                    **messages 배열의 role 값:**
                    - `user` : 사용자가 보낸 메시지
                    - `assistant` : AI 응답 메시지

                    **messages 배열의 chartType 값 (AI 응답에만 존재):**
                    - `null` : 일반 텍스트 응답
                    - `bar` / `line` / `pie` : 해당 차트 타입, `data` 배열에 차트 데이터 포함
                    - `table` : 테이블 형태, `data` 배열에 행 데이터 포함
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConversationResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "conversationId": "conv-abc123",
                                        "title": "이번 주 광고 성과 분석",
                                        "createdAt": 1709740800000,
                                        "updatedAt": 1709827200000,
                                        "messages": [
                                          {
                                            "messageId": "msg-001",
                                            "role": "user",
                                            "content": "이번 주 카카오 광고 성과 알려줘",
                                            "createdAt": "2024-03-07T10:00:00",
                                            "chartType": null,
                                            "data": null
                                          },
                                          {
                                            "messageId": "msg-002",
                                            "role": "assistant",
                                            "content": "이번 주 카카오 광고 비용은 1,200,000원입니다.",
                                            "createdAt": "2024-03-07T10:00:05",
                                            "chartType": "bar",
                                            "data": [{"date": "2024-03-01", "cost": 150000}]
                                          }
                                        ]
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음 — 본인 대화가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대화를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"NOT_FOUND\",\"message\":\"대화를 찾을 수 없습니다.\"}")))
    })
    @GetMapping("/{conversationId}")
    ApiResponse<ConversationResponse> get(
            @Parameter(description = "대화 ID", example = "conv-abc123")
            @PathVariable String conversationId,
            @Parameter(hidden = true) String userId
    );

    @Operation(
            summary = "대화 제목 수정",
            description = "대화 제목을 변경합니다. 사이드바 인라인 편집 기능에 사용하세요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":true,\"data\":null}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대화를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"NOT_FOUND\",\"message\":\"대화를 찾을 수 없습니다.\"}")))
    })
    @PatchMapping("/{conversationId}/title")
    ApiResponse<Void> updateTitle(
            @Parameter(description = "대화 ID", example = "conv-abc123")
            @PathVariable String conversationId,
            @RequestBody UpdateTitleRequest request,
            @Parameter(hidden = true) String userId
    );

    @Operation(
            summary = "대화 삭제",
            description = "대화와 해당 대화의 모든 메시지를 삭제합니다. **삭제 후 복구 불가**"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":true,\"data\":null}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대화를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"NOT_FOUND\",\"message\":\"대화를 찾을 수 없습니다.\"}")))
    })
    @DeleteMapping("/{conversationId}")
    ApiResponse<Void> delete(
            @Parameter(description = "대화 ID", example = "conv-abc123")
            @PathVariable String conversationId,
            @Parameter(hidden = true) String userId
    );
}
