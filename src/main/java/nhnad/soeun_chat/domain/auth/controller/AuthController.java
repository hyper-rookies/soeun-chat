package nhnad.soeun_chat.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Cognito Hosted UI 콜백", description = "authorization_code를 Cognito ID 토큰으로 교환합니다.")
    @PostMapping("/callback")
    public ApiResponse<AuthResponse> callback(@Valid @RequestBody CallbackRequest request) {
        return ApiResponse.of(authService.callback(request.code(), request.redirectUri()));
    }

    @Operation(summary = "액세스 토큰 갱신", description = "refresh_token으로 새로운 액세스 토큰을 발급합니다.")
    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.of(authService.refresh(request.refreshToken()));
    }
}
