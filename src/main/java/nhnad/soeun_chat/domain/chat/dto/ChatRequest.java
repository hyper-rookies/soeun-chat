package nhnad.soeun_chat.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "채팅 요청")
public record ChatRequest(
        @Schema(description = "사용자 메시지", example = "이번 주 카카오 광고 성과 알려줘")
        @NotBlank String message
) {}
