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

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.cometd.annotation.Service;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.demo.model.RoomInfo;
import org.cometd.demo.model.UserInfo;
import org.cometd.oort.Oort;
import org.cometd.oort.OortService;

/**
 * {@link RoomEditService} performs the actions needed to change the room name and broadcast the change across
 * the cluster.
 * <p />
 * A specific chat room is owned by a particular node, and the {@link RoomInfo} that represents the room is
 * distributed in all nodes. Since {@link RoomInfo} are immutable and are owned by a particular node, this
 * service needs to forward the edit action to the node that owns the room being edited, and as such extends
 * {@link OortService}.
 * <p />
 * When the action is executed on the node that owns the room, the {@link RoomInfo} for the room being edited
 * is replaced by a new instance containing the edited name, and the change broadcast to all nodes.
 */
@Service(RoomEditService.NAME)
public class RoomEditService extends OortService<RoomInfo, OortService.ServerContext>
{
    public static final String NAME = "room_edit";
    private static final String USER_ID = "userId";
    private static final String ROOM_ID = "roomId";
    private static final String ROOM_NAME = "roomName";

    private final UsersService usersService;
    private final RoomsService roomsService;
    private final RoomMembersService membersService;

    public RoomEditService(Oort oort, UsersService usersService, RoomsService roomsService, RoomMembersService membersService)
    {
        super(oort, NAME);
        this.usersService = usersService;
        this.roomsService = roomsService;
        this.membersService = membersService;
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

    @org.cometd.annotation.Listener("/service/room/edit")
    public void joinRoom(final ServerSession remote, final ServerMessage message)
    {
        logger.debug("Edit room request from {}: {}", remote, message);
        Map<String, Object> data = message.getDataAsMap();
        Map<String, Object> actionData = new HashMap<>(data);
        actionData.put(USER_ID, usersService.getUserInfo(remote).getId());
        long roomId = ((Number)data.get(ROOM_ID)).longValue();
        String oortURL = roomsService.findOortURLFor(roomId);
        if (oortURL != null)
        {
            forward(oortURL, actionData, new ServerContext(remote, message));
        }
        else
        {
            editFailed(remote, "Cannot edit room, unknown owner node");
        }
    }

    @Override
    protected Result<RoomInfo> onForward(Request request)
    {
        Map<String, Object> data = request.getDataAsMap();
        long roomId = ((Number)data.get(ROOM_ID)).longValue();
        RoomInfo roomInfo = roomsService.getRoomInfo(roomId);
        if (roomInfo != null)
        {
            String userId = (String)data.get(USER_ID);
            UserInfo userInfo = usersService.find(userId);
            if (userInfo != null)
            {
                // Can only edit if user has joined the room
                if (membersService.isMember(roomInfo, userInfo))
                {
                    String newName = (String)data.get(ROOM_NAME);
                    if (newName != null)
                    {
                        RoomInfo newRoomInfo = new RoomInfo(roomInfo.getId(), newName, roomInfo.getMembership());
                        roomsService.replaceRoomInfo(newRoomInfo);
                        return Result.success(newRoomInfo);
                    }
                    else
                    {
                        return Result.failure("Cannot edit room, no new room name");
                    }
                }
                else
                {
                    return Result.failure("Cannot edit room, user not member of the room");
                }
            }
            else
            {
                return Result.ignore("Cannot edit room, unknown user");
            }
        }
        else
        {
            return Result.ignore("Cannot edit room, unknown room");
        }
    }

    @Override
    protected void onForwardSucceeded(RoomInfo roomInfo, ServerContext context)
    {
        logger.debug("Edit room request succeeded");
        ServerSession session = context.getServerSession();
        UserInfo userInfo = usersService.getUserInfo(session);
        logger.debug("Delivering room to {}: {}", userInfo, roomInfo);
        session.deliver(getLocalSession(), context.getServerMessage().getChannel(), roomInfo);
        roomsService.broadcastRooms();
    }

    @Override
    protected void onForwardFailed(Object failure, ServerContext context)
    {
        editFailed(context.getServerSession(), String.valueOf(failure));
    }

    private void editFailed(ServerSession remote, String message)
    {
        remote.deliver(getLocalSession(), "/service/status", message);
    }
}
