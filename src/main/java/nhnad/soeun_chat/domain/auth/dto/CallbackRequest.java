package nhnad.soeun_chat.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CallbackRequest(
        @NotBlank String code,
        @NotBlank String redirectUri
) {}
