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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.cometd.annotation.Service;
import org.cometd.demo.model.ChatHistoryInfo;
import org.cometd.demo.model.ChatInfo;
import org.cometd.demo.model.RoomChatInfo;
import org.cometd.demo.model.RoomInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ChatHistoryService} acts as the storage for chat room messages.
 * <p />
 * An instance of this service is present in all nodes, and it stores only the messages for the rooms
 * that are owned by the node.
 * <p />
 * This service does not use any Oort features, it is just wrapper for a map from room id to
 * {@link ChatHistoryInfo} instances, to be used by other services like {@link ChatHistoryArchiveService}
 * and {@link ChatHistoryRequestService}.
 */
@Service(ChatHistoryService.NAME)
public class ChatHistoryService
{
    public static final String NAME = "chat_history";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ConcurrentMap<Long, ChatHistoryInfo> roomToHistory = new ConcurrentHashMap<>();
    private final int maxEntries;

    public ChatHistoryService(int maxEntries)
    {
        this.maxEntries = maxEntries;
    }

    public void archive(RoomChatInfo roomChatInfo)
    {
        RoomInfo roomInfo = roomChatInfo.getRoomInfo();
        long roomId = roomInfo.getId();
        ChatHistoryInfo roomHistory = roomToHistory.get(roomId);
        if (roomHistory == null)
        {
            roomHistory = new ChatHistoryInfo(roomInfo, maxEntries);
            ChatHistoryInfo existing = roomToHistory.putIfAbsent(roomId, roomHistory);
            if (existing != null)
                roomHistory = existing;
        }
        ChatInfo chatInfo = roomChatInfo.getChatInfo();
        ChatInfo discarded = roomHistory.add(chatInfo);
        if (discarded != null)
            logger.debug("Dearchiving old chat info {}", discarded);
        logger.debug("Archived chat info {}", chatInfo);
    }

    public ChatHistoryInfo retrieve(RoomInfo roomInfo)
    {
        ChatHistoryInfo roomHistory = roomToHistory.get(roomInfo.getId());
        if (roomHistory == null)
            roomHistory = new ChatHistoryInfo(roomInfo, maxEntries);
        return roomHistory;
    }
}
