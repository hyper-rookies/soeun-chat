package nhnad.soeun_chat.domain.conversation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import nhnad.soeun_chat.domain.conversation.dto.ConversationResponse;
import nhnad.soeun_chat.domain.conversation.dto.ConversationSummary;
import nhnad.soeun_chat.domain.conversation.dto.CreateConversationRequest;
import nhnad.soeun_chat.domain.conversation.dto.UpdateTitleRequest;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Conversation", description = "대화 관리")
@RequestMapping("/api/conversations")
public interface ConversationApi {

    @Operation(summary = "새 대화 생성")
    @PostMapping
    ApiResponse<ConversationResponse> create(
            @RequestBody CreateConversationRequest request,
            @Parameter(hidden = true) String userId
    );

    @Operation(summary = "대화 목록 조회 (최신순)")
    @GetMapping
    ApiResponse<List<ConversationSummary>> list(
            @Parameter(hidden = true) String userId
    );

    @Operation(summary = "특정 대화 조회 (메시지 포함)")
    @GetMapping("/{conversationId}")
    ApiResponse<ConversationResponse> get(
            @Parameter(description = "대화 ID") @PathVariable String conversationId,
            @Parameter(hidden = true) String userId
    );

    @Operation(summary = "대화 제목 수정")
    @PatchMapping("/{conversationId}/title")
    ApiResponse<Void> updateTitle(
            @Parameter(description = "대화 ID") @PathVariable String conversationId,
            @RequestBody UpdateTitleRequest request,
            @Parameter(hidden = true) String userId
    );

    @Operation(summary = "대화 삭제 (메시지 포함)")
    @DeleteMapping("/{conversationId}")
    ApiResponse<Void> delete(
            @Parameter(description = "대화 ID") @PathVariable String conversationId,
            @Parameter(hidden = true) String userId
    );
}
