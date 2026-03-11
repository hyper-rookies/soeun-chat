package nhnad.soeun_chat.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhnad.soeun_chat.domain.auth.dto.AuthResponse;
import nhnad.soeun_chat.domain.auth.dto.CallbackRequest;
import nhnad.soeun_chat.domain.auth.dto.RefreshRequest;
import nhnad.soeun_chat.domain.auth.dto.RefreshResponse;
import nhnad.soeun_chat.domain.auth.service.AuthService;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 — Google 소셜 로그인 및 토큰 관리")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Google 로그인 콜백",
            description = """
                    Cognito Hosted UI에서 받은 `authorization_code`를 서버에 전달하여 `accessToken`과 `refreshToken`을 발급받습니다.

                    **전체 로그인 흐름:**
                    1. 프론트엔드에서 Cognito Hosted UI 로그인 페이지로 리다이렉트
                    2. Google 로그인 완료 후 Cognito가 `redirectUri?code=xxx` 로 리다이렉트
                    3. 프론트엔드에서 URL의 `code` 파라미터를 추출하여 **이 API 호출**
                    4. 응답의 `accessToken`을 `Authorization: Bearer {token}` 형태로 저장하여 이후 API 호출에 사용

                    > ⚠️ 이 API는 인증 없이 호출 가능합니다 (Authorization 헤더 불필요).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공 — accessToken, refreshToken 반환",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "accessToken": "eyJraWQiOi...",
                                        "refreshToken": "eyJjdHki...",
                                        "userId": "550e8400-e29b-41d4-a716-446655440000",
                                        "email": "user@example.com",
                                        "name": "홍길동"
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 — code 또는 redirectUri 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"INVALID_INPUT\",\"message\":\"필수 파라미터가 누락되었습니다.\"}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 — 유효하지 않거나 만료된 code, 또는 잘못된 redirectUri",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"INVALID_TOKEN\",\"message\":\"유효하지 않은 인증 코드입니다.\"}")
                    )
            )
    })
    @SecurityRequirements
    @PostMapping("/callback")
    public ApiResponse<AuthResponse> callback(@Valid @RequestBody CallbackRequest request) {
        return ApiResponse.of(authService.callback(request.code(), request.redirectUri()));
    }

    @Operation(
            summary = "액세스 토큰 갱신",
            description = """
                    `refreshToken`으로 새로운 `accessToken`을 발급합니다.

                    `accessToken`이 만료(401 응답)될 때 이 API를 호출하여 자동 갱신하세요.
                    갱신 후 저장된 `accessToken`을 새 값으로 교체하고 원래 요청을 재시도합니다.

                    > ⚠️ 이 API는 인증 없이 호출 가능합니다 (Authorization 헤더 불필요).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "토큰 갱신 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "accessToken": "eyJraWQiOi...",
                                        "expiresIn": 3600
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 — refreshToken 누락",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"INVALID_INPUT\",\"message\":\"refreshToken이 누락되었습니다.\"}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "토큰 갱신 실패 — refreshToken 만료 또는 유효하지 않음 (재로그인 필요)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"INVALID_TOKEN\",\"message\":\"refreshToken이 만료되었습니다. 다시 로그인하세요.\"}")
                    )
            )
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.of(authService.refresh(request.refreshToken()));
    }
}
