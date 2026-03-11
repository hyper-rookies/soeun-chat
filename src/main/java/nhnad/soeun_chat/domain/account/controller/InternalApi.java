package nhnad.soeun_chat.domain.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import nhnad.soeun_chat.domain.account.dto.UserItem;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Tag(name = "Internal", description = "내부 관리 API — 서버 간 통신 전용 (프론트엔드 직접 호출 금지)")
@RequestMapping("/api/internal")
public interface InternalApi {

    @Operation(
            summary = "Cognito 유저 목록 조회 (내부 API)",
            description = """
                    Cognito User Pool에 등록된 **전체 유저 목록**을 반환합니다.

                    > ⚠️ **프론트엔드에서 직접 호출하지 않습니다.**
                    > 배치 Lambda 또는 서버 간 통신에서만 사용하는 내부 API입니다.

                    **인증:** `X-Internal-Key` 헤더에 내부 API 키를 포함해야 합니다. (Bearer 토큰 불필요)

                    **응답 필드:**
                    | 필드 | 설명 |
                    |---|---|
                    | `userId` | Cognito sub (UUID 형식) |
                    | `email` | 사용자 이메일 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserItem.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "userId": "550e8400-e29b-41d4-a716-446655440000",
                                          "email": "user@example.com"
                                        }
                                      ]
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "X-Internal-Key 누락 또는 불일치",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"INVALID_TOKEN\",\"message\":\"유효하지 않은 내부 API 키입니다.\"}")
                    )
            )
    })
    @SecurityRequirements
    @GetMapping("/users")
    ApiResponse<List<UserItem>> listUsers(
            @Parameter(in = ParameterIn.HEADER, name = "X-Internal-Key", required = true,
                    description = "내부 API 키 — 서버/Lambda 전용")
            @RequestHeader("X-Internal-Key") String internalKey
    );
}
