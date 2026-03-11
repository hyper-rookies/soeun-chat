package nhnad.soeun_chat.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청")
public record RefreshRequest(
        @Schema(description = "로그인 시 발급받은 refreshToken", example = "eyJjdHkiOiJKV1Qi...")
        @NotBlank String refreshToken
) {}
