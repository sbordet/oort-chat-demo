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
 * {@link RoomJoinService} performs the actions needed to join a particular user to a particular chat room.
 * <p />
 * Joining a room is subject to some checks, in particular that the user membership level implies the room
 * membership level.
 * <p />
 * Both {@link UserInfo} and {@link RoomInfo} have the membership information, and this implies that the
 * checks can be performed locally in any node, no matter if the node owns the room or if it owns the user.
 * <p />
 * If, for example, {@link RoomInfo} did not have the membership information, we would have needed to forward
 * the join action to the node that owned the room (which would have had the membership information locally).
 * <p />
 * Because joining a room is local to a node, {@link RoomLeaveService} must work locally too.
 */
@Service(RoomJoinService.NAME)
public class RoomJoinService
{
    public static final String NAME = "room_join";
    private static final String ROOM_ID = "roomId";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final UsersService usersService;
    private final RoomsService roomsService;
    private final RoomMembersService membersService;
    private final ChatHistoryRequestService historyService;
    @Session
    private LocalSession session;

    public RoomJoinService(UsersService usersService, RoomsService roomsService, RoomMembersService membersService, ChatHistoryRequestService historyService)
    {
        this.usersService = usersService;
        this.roomsService = roomsService;
        this.membersService = membersService;
        this.historyService = historyService;
    }

    /**
     * Remote clients will send a message to join a room that is handled by this method.
     *
     * @param remote the remote client
     * @param message the join message
     */
    @Listener("/service/room/join")
    public void joinRoom(ServerSession remote, ServerMessage message)
    {
        logger.debug("Join room request from {}: {}", remote, message);
        Map<String, Object> data = message.getDataAsMap();
        long roomId = ((Number)data.get(ROOM_ID)).longValue();
        RoomInfo roomInfo = roomsService.findRoomInfo(roomId);
        if (roomInfo != null)
        {
            UserInfo userInfo = usersService.getUserInfo(remote);
            if (userInfo != null)
            {
                // Can only join if membership allows it
                if (roomInfo.getMembership().implies(userInfo.getMembership()))
                {
                    if (membersService.join(roomInfo, userInfo))
                    {
                        logger.debug("Join room request succeeded");
                        logger.debug("Delivering room to {}: {}", userInfo, roomInfo);
                        remote.deliver(session, message.getChannel(), roomInfo);
                        membersService.deliverMembers(remote, userInfo, roomInfo);
                        historyService.deliverChatHistory(remote, roomInfo);
                    }
                    else
                    {
                        joinFailed(remote, "Cannot join room, no members for room " + roomInfo);
                    }
                }
                else
                {
                    joinFailed(remote, "Cannot join room, no permission to join room " + roomInfo);
                }
            }
            else
            {
                joinFailed(remote, "Cannot join room, unknown user");
            }
        }
        else
        {
            joinFailed(remote, "Cannot join room, unknown room");
        }
    }

    private void joinFailed(ServerSession remote, String message)
    {
        remote.deliver(session, "/service/status", message);
    }
}
