package nhnad.soeun_chat.domain.conversation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.conversation.dto.ConversationResponse;
import nhnad.soeun_chat.domain.conversation.dto.ConversationSummary;
import nhnad.soeun_chat.domain.conversation.dto.CreateConversationRequest;
import nhnad.soeun_chat.domain.conversation.dto.UpdateTitleRequest;
import nhnad.soeun_chat.domain.conversation.service.ConversationService;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ConversationController implements ConversationApi {

    private final ConversationService conversationService;

    @Override
    public ApiResponse<ConversationResponse> create(
            @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("대화 생성 요청 - userId: {}, title: {}", userId, request.title());
        return ApiResponse.of(conversationService.createConversation(userId, request.title()));
    }

    @Override
    public ApiResponse<List<ConversationSummary>> list(
            @AuthenticationPrincipal String userId) {

        log.info("대화 목록 조회 - userId: {}", userId);
        return ApiResponse.of(conversationService.getConversations(userId));
    }

    @Override
    public ApiResponse<ConversationResponse> get(
            @PathVariable String conversationId,
            @AuthenticationPrincipal String userId) {

        log.info("대화 조회 - conversationId: {}, userId: {}", conversationId, userId);
        return ApiResponse.of(conversationService.getConversation(userId, conversationId));
    }

    @Override
    public ApiResponse<Void> updateTitle(
            @PathVariable String conversationId,
            @RequestBody UpdateTitleRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("대화 제목 수정 - conversationId: {}, userId: {}", conversationId, userId);
        conversationService.updateTitle(conversationId, userId, request.title());
        return ApiResponse.ok();
    }

    @Override
    public ApiResponse<Void> delete(
            @PathVariable String conversationId,
            @AuthenticationPrincipal String userId) {

        log.info("대화 삭제 - conversationId: {}, userId: {}", conversationId, userId);
        conversationService.deleteConversation(userId, conversationId);
        return ApiResponse.ok();
    }
}
