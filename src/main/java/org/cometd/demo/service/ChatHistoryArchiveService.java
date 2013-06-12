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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.cometd.annotation.Service;
import org.cometd.demo.model.RoomChatInfo;
import org.cometd.demo.model.RoomInfo;
import org.cometd.oort.Oort;
import org.cometd.oort.OortService;

/**
 * {@link ChatHistoryArchiveService} is responsible to send the chat messages to archive to the right node.
 * <p />
 * Chat messages are archived in the node that owns the room by an instance of {@link ChatHistoryService}.
 */
@Service(ChatHistoryArchiveService.NAME)
public class ChatHistoryArchiveService extends OortService<Void, Void>
{
    public static final String NAME = "chat_history_archive";

    private final RoomsService roomsService;
    private final ChatHistoryService chatHistoryService;

    public ChatHistoryArchiveService(Oort oort, RoomsService roomsService, ChatHistoryService chatHistoryService)
    {
        super(oort, NAME);
        this.roomsService = roomsService;
        this.chatHistoryService = chatHistoryService;
    }

    @PostConstruct
    public void construct() throws Exception
    {
        start();
    }

    @PreDestroy
    public void destroy() throws Exception
    {
        stop();
    }

    public void archive(RoomChatInfo roomChatInfo)
    {
        RoomInfo roomInfo = roomChatInfo.getRoomInfo();
        String oortURL = roomsService.findOortURLFor(roomInfo.getId());
        if (oortURL != null)
            forward(oortURL, roomChatInfo, null);
    }

    @Override
    protected Result<Void> onForward(Request request)
    {
        final RoomChatInfo roomChatInfo = (RoomChatInfo)request.getData();
        chatHistoryService.archive(roomChatInfo);
        return null;
    }

    @Override
    protected void onForwardSucceeded(Void result, Void context)
    {
        // Nothing to do
    }

    @Override
    protected void onForwardFailed(Object failure, Void context)
    {
        // Nothing to do
    }
}
