package nhnad.soeun_chat.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cognito 유저 정보")
public record UserItem(
        @Schema(description = "사용자 고유 ID (Cognito sub, UUID 형식)", example = "550e8400-e29b-41d4-a716-446655440000")
        String userId,

        @Schema(description = "사용자 이메일", example = "user@example.com")
        String email
) {}
