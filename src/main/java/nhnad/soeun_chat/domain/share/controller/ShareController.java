package nhnad.soeun_chat.domain.share.controller;

import lombok.RequiredArgsConstructor;
import nhnad.soeun_chat.domain.conversation.dto.ConversationResponse;
import nhnad.soeun_chat.domain.share.dto.ShareCreateResponse;
import nhnad.soeun_chat.domain.share.service.ShareService;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ShareController implements ShareApi {

    private final ShareService shareService;

    @Override
    public ApiResponse<ShareCreateResponse> createShareLink(
            @AuthenticationPrincipal String userId,
            @PathVariable String conversationId) {

        return ApiResponse.of(shareService.generateShareToken(conversationId, userId));
    }

    @Override
    public ApiResponse<ConversationResponse> getSharedConversation(
            @PathVariable String token) {

        return ApiResponse.of(shareService.getSharedConversation(token));
    }
}
