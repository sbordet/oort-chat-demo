/*
 * Copyright (c) 2013-2020 the original author or authors.
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

public class RoomChatInfo {
    private final RoomInfo roomInfo;
    private final ChatInfo chatInfo;

    public RoomChatInfo(RoomInfo roomInfo, ChatInfo chatInfo) {
        this.roomInfo = roomInfo;
        this.chatInfo = chatInfo;
    }

    public RoomInfo getRoomInfo() {
        return roomInfo;
    }

    public ChatInfo getChatInfo() {
        return chatInfo;
    }
}
