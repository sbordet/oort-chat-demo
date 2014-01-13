/*
 * Copyright (c) 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cometd.demo.service;

import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.cometd.annotation.Listener;
import org.cometd.annotation.Service;
import org.cometd.annotation.Session;
import org.cometd.bayeux.ChannelId;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.demo.model.ChatInfo;
import org.cometd.demo.model.RoomChatInfo;
import org.cometd.demo.model.RoomInfo;
import org.cometd.demo.model.UserInfo;
import org.cometd.oort.Oort;

/**
 * {@link ChatService} performs the actions needed to distribute a chat message across nodes.
 * <p />
 * Remote clients send the chat message to the node they are connected using a
 * {@link ChannelId#isService() service channel}, so that the server can perform additional checks
 * (like bad word substitution) and rebroadcast a possibly different message.
 * <p />
 * Chat messages broadcasting is done via standard Oort features (by observing the {@code /chat/*}
 * channel).
 * <p />
 * Chat messages are then sent to the {@link ChatHistoryArchiveService} for archival.
 */
@Service(ChatService.NAME)
public class ChatService
{
    public static final String NAME = "chat";
    private static final String TEXT = "text";
    private static final String ROOM_ID = "roomId";

    private final Oort oort;
    private final UsersService usersService;
    private final RoomsService roomsService;
    private final ChatHistoryArchiveService archiveService;
    @Session
    private LocalSession session;

    public ChatService(Oort oort, UsersService usersService, RoomsService roomsService, ChatHistoryArchiveService archiveService)
    {
        this.oort = oort;
        this.usersService = usersService;
        this.roomsService = roomsService;
        this.archiveService = archiveService;
    }

    @PostConstruct
    private void construct()
    {
        // By observing /chat/* we can broadcast chat messages to all clients of all nodes
        oort.observeChannel("/chat/*");
    }

    @PreDestroy
    private void destroy()
    {
        oort.deobserveChannel("/chat/*");
    }

    @Listener("/service/chat")
    public void chat(ServerSession remote, ServerMessage message)
    {
        Map<String, Object> data = message.getDataAsMap();
        String text = (String)data.get(TEXT);

        // Replace bad words
        String newText = text.replaceAll("\\b(dang)\\b", "dong");

        // Broadcast the message
        UserInfo userInfo = usersService.getUserInfo(remote);
        ChatInfo chatInfo = new ChatInfo(userInfo, newText);
        long roomId = ((Number)data.get(ROOM_ID)).longValue();
        String channelName = "/chat/" + roomId;
        oort.getBayeuxServer().getChannel(channelName).publish(session, chatInfo);

        // Store the chat history
        RoomInfo roomInfo = roomsService.findRoomInfo(roomId);
        RoomChatInfo roomChatInfo = new RoomChatInfo(roomInfo, chatInfo);
        archiveService.archive(roomChatInfo);
    }
}
