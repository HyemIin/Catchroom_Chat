package com.catchroom.chat.chatroom.service;

import com.catchroom.chat.chatroom.dto.ChatRoomGetResponse;
import com.catchroom.chat.feign.client.MainFeignClient;
import com.catchroom.chat.global.common.SuccessMessage;
import com.catchroom.chat.message.dto.ChatMessageDto;
import com.catchroom.chat.message.entity.ChatMessage;
import com.catchroom.chat.message.repository.ChatRoomRedisRepository;
import com.catchroom.chat.message.service.ChatMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class ChatRoomService {
    private final MainFeignClient mainFeignClient;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final ChatMongoService chatMongoService;

    public ChatRoomGetResponse getChatRoomInfo(String accessToken, String roomId) {
        return mainFeignClient.getChatRoomInfo(accessToken, roomId);
    }

    public List<ChatRoomGetResponse> getChatRoomListByFeign(Long userId, String accessToken) {
        // 처음 HTTP 요청에서는 무조건 레디스 초기화 진행하도록 로직 수정
        List<ChatRoomGetResponse> chatRoomListGetResponseList = mainFeignClient.getChatRoomList(accessToken);
        chatRoomListGetResponseList.forEach(this::setListChatLastMessage);
        chatRoomRedisRepository.initChatRoomList(userId, chatRoomListGetResponseList);
        return sortChatRoomListLatest(chatRoomListGetResponseList);
    }

    public List<ChatRoomGetResponse> getChatRoomList(Long userId, String accessToken) {

        List<ChatRoomGetResponse> chatRoomListGetResponseList = null;
        if (chatRoomRedisRepository.existChatRoomList(userId)) {
            chatRoomListGetResponseList = chatRoomRedisRepository.getChatRoomList(userId);

        } else {
            // 채팅방이 레디스에 없으면 페인 사용해서 불러온다!
            chatRoomListGetResponseList = mainFeignClient.getChatRoomList(accessToken);
            chatRoomRedisRepository.initChatRoomList(userId, chatRoomListGetResponseList);
        }

        chatRoomListGetResponseList.forEach(this::setListChatLastMessage);

        return chatRoomListGetResponseList;
    }

    /**
     * 몽고 디비에서 마지막 메시지 가져와서 저장하는 로직
     * @param chatRoomListGetResponse
     */
    public void setListChatLastMessage(ChatRoomGetResponse chatRoomListGetResponse) {

        // 몽고 디비에서 마지막 메시지 가져와서 저장.
        String chatRoomNumber = chatRoomListGetResponse.getChatRoomNumber();
        if (chatRoomRedisRepository.getLastMessage(chatRoomNumber) != null) {
            chatRoomListGetResponse.updateChatMessageDto(
                chatRoomRedisRepository.getLastMessage(chatRoomNumber)
            );
        } else {
            ChatMessage chatMessage = chatMongoService.findLatestMessageByRoomId(chatRoomNumber);
            if (chatMessage != null) {
                chatRoomListGetResponse.updateChatMessageDto(
                    ChatMessageDto.fromEntity(chatMessage)
                );
            }
        }
    }

    /**
     * 채팅방 마지막 메시지의 시간들을 비교하여 정렬하는 메소드
     * @param chatRoomListGetResponseList
     */
    public List<ChatRoomGetResponse> sortChatRoomListLatest (
            List<ChatRoomGetResponse> chatRoomListGetResponseList
    ) {
        List<ChatRoomGetResponse> newChatRoomList = new ArrayList<>();
        for (ChatRoomGetResponse response : chatRoomListGetResponseList) {
            if (response.getLastChatmessageDto() != null) newChatRoomList.add(response);
        }

        Collections.sort(newChatRoomList, (o1, o2) ->
                o2.getLastChatmessageDto().getTime().compareTo(o1.getLastChatmessageDto().getTime()));

        return newChatRoomList;
    }

    /**
     * 채팅방 삭제 로직
     * @param accessToken
     * @param roomId
     */
    public void deleteChatRoom(String accessToken, String roomId, Long userId) {
        log.info("=>> 채팅방 삭제 {} start ", roomId);
        SuccessMessage message = mainFeignClient.deleteChatRoom(accessToken, roomId);
        chatRoomRedisRepository.deleteChatRoom(userId, roomId);
        log.info("=>> 채팅방 삭제 {} Msg : {}", roomId, message.Meassage());
    }


}
