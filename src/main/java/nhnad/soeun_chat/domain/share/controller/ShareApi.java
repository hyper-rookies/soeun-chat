package nhnad.soeun_chat.domain.share.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import nhnad.soeun_chat.domain.conversation.dto.ConversationResponse;
import nhnad.soeun_chat.domain.share.dto.ShareCreateResponse;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Share", description = "대화 공유 링크 생성 및 조회")
@RequestMapping("/api/share")
public interface ShareApi {

    @Operation(
            summary = "공유 링크 생성",
            description = """
                    특정 대화에 대한 **공유 링크 토큰**을 생성합니다.

                    생성된 `shareToken`을 URL에 포함하여 공유 링크를 만드세요:
                    ```
                    https://your-app.com/share/{shareToken}
                    ```

                    공유 링크는 만료 시각(`expiresAt`) 이후 접근 불가합니다.
                    동일한 대화에 대해 여러 번 호출하면 새 토큰이 발급됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공유 링크 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ShareCreateResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "shareToken": "sh_eyJhbGci...",
                                        "shareUrl": "https://soeun-report.vercel.app/share/sh_eyJhbGci...",
                                        "expiresAt": "2024-04-07T10:00:00"
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음 — 본인 대화가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "대화를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"NOT_FOUND\",\"message\":\"대화를 찾을 수 없습니다.\"}")
                    )
            )
    })
    @PostMapping("/{conversationId}")
    ApiResponse<ShareCreateResponse> createShareLink(
            @Parameter(hidden = true) @AuthenticationPrincipal String userId,
            @Parameter(description = "공유할 대화 ID", example = "conv-abc123")
            @PathVariable String conversationId
    );

    @Operation(
            summary = "공유 대화 조회",
            description = """
                    공유 토큰으로 대화 내용을 조회합니다. **로그인 불필요** (비인증 접근 가능)

                    공유 페이지(`/share/{token}`)에서 대화 내용을 표시할 때 사용하세요.
                    토큰이 만료되었거나 유효하지 않으면 `401` 을 반환합니다.
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
                                          }
                                        ]
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "공유 토큰 만료 또는 유효하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"INVALID_TOKEN\",\"message\":\"공유 링크가 만료되었거나 유효하지 않습니다.\"}")
                    )
            )
    })
    @SecurityRequirements
    @GetMapping("/{token}")
    ApiResponse<ConversationResponse> getSharedConversation(
            @Parameter(description = "공유 토큰 — `POST /api/share/{conversationId}` 에서 발급", example = "sh_eyJhbGci...")
            @PathVariable String token
    );
}
