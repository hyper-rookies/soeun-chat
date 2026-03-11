package nhnad.soeun_chat.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google OAuth 콜백 요청")
public record CallbackRequest(
        @Schema(description = "Cognito Hosted UI에서 발급된 authorization_code", example = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
        @NotBlank String code,

        @Schema(description = "OAuth 요청 시 사용한 redirectUri (Cognito에 등록된 값과 일치해야 함)", example = "https://soeun-report.vercel.app/auth/callback")
        @NotBlank String redirectUri
) {}
