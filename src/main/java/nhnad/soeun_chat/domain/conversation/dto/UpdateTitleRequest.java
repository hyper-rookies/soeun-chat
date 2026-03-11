package nhnad.soeun_chat.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대화 제목 수정 요청")
public record UpdateTitleRequest(
        @Schema(description = "변경할 제목", example = "수정된 제목")
        String title
) {}
