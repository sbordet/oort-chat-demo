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

import org.cometd.annotation.Listener;
import org.cometd.annotation.Service;
import org.cometd.annotation.Session;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.demo.model.RoomInfo;
import org.cometd.demo.model.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RoomLeaveService} performs the actions needed to leave a particular user from a particular chat room.
 *
 * @see RoomJoinService
 */
@Service(RoomLeaveService.NAME)
public class RoomLeaveService
{
    public static final String NAME = "room_leave";
    private static final String ROOM_ID = "roomId";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final UsersService usersService;
    private final RoomsService roomsService;
    private final RoomMembersService membersService;
    @Session
    private LocalSession session;

    public RoomLeaveService(UsersService usersService, RoomsService roomsService, RoomMembersService membersService)
    {
        this.usersService = usersService;
        this.roomsService = roomsService;
        this.membersService = membersService;
    }

    @Listener("/service/room/leave")
    public void joinRoom(final ServerSession remote, final ServerMessage message)
    {
        logger.debug("Leave room request from {}: {}", remote, message);
        Map<String, Object> data = message.getDataAsMap();
        long roomId = ((Number)data.get(ROOM_ID)).longValue();
        RoomInfo roomInfo = roomsService.findRoomInfo(roomId);
        if (roomInfo != null)
        {
            UserInfo userInfo = usersService.getUserInfo(remote);
            if (userInfo != null)
            {
                membersService.leave(roomInfo, userInfo);
                logger.debug("Leave room request succeeded");
                logger.debug("Delivering room to {}: {}", userInfo, roomInfo);
                remote.deliver(session, message.getChannel(), roomInfo);
            }
            else
            {
                leaveFailed(remote, "Cannot leave room, unknown user");
            }
        }
        else
        {
            leaveFailed(remote, "Cannot leave room, unknown room");
        }
    }

    private void leaveFailed(ServerSession remote, String message)
    {
        remote.deliver(session, "/service/status", message);
    }
}
