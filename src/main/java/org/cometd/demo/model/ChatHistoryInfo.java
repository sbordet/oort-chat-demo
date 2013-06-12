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

package org.cometd.demo.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ChatHistoryInfo
{
    private final List<ChatInfo> chatInfos = new LinkedList<>();
    private final RoomInfo roomInfo;
    private final int maxEntries;

    public ChatHistoryInfo(RoomInfo roomInfo, int maxEntries)
    {
        this.roomInfo = roomInfo;
        this.maxEntries = maxEntries;
    }

    public RoomInfo getRoomInfo()
    {
        return roomInfo;
    }

    public int getMaxEntries()
    {
        return maxEntries;
    }

    public List<ChatInfo> getChatInfos()
    {
        synchronized (this)
        {
            return new ArrayList<>(chatInfos);
        }
    }

    public ChatInfo add(ChatInfo chatInfo)
    {
        synchronized (this)
        {
            ChatInfo result = null;
            if (chatInfos.size() == maxEntries)
                result = chatInfos.remove(0);
            chatInfos.add(chatInfo);
            return result;
        }
    }
}
