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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.cometd.annotation.Service;
import org.cometd.annotation.Session;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ConfigurableServerChannel;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.demo.model.RoomInfo;
import org.cometd.demo.model.UserInfo;
import org.cometd.oort.Oort;
import org.cometd.oort.OortList;
import org.cometd.oort.OortObjectFactories;
import org.cometd.oort.OortObjectMergers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RoomMembersService} maintains the members list for each room.
 * <p />
 * Every time a room is created/destroyed, this service is informed and will create/destroy the
 * members list for that room, see {@link #roomAdded(RoomInfo)} and {@link #roomRemoved(RoomInfo)}.
 * <p />
 * This service registers itself as a {@link BayeuxServer.SessionListener} in
 * order to be notified when users disconnect, and update the member list accordingly.
 * <p />
 * When a user joins or leaves a room, a message is broadcast using standard Oort features
 * via {@link Oort#observeChannel(String)}, see {@link #construct()} to all users of all nodes.
 * For an alternative way of notifying all users of all nodes, see discussion at {@link RoomsService}.
 */
@Service(RoomMembersService.NAME)
public class RoomMembersService implements BayeuxServer.SessionListener
{
    public static final String NAME = "room_members";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ConcurrentMap<RoomInfo, OortList<UserInfo>> roomToMembers = new ConcurrentHashMap<>();
    private final Oort oort;
    private final UsersService usersService;
    @Session
    private LocalSession session;

    public RoomMembersService(Oort oort, UsersService usersService)
    {
        this.oort = oort;
        this.usersService = usersService;
    }

    @PostConstruct
    private void construct()
    {
        // By observing /members/* we can broadcast member changes to all clients of all nodes
        // from the node that owns the room. This solution is the one that requires less code.
        // An alternative solution is to have a listener for Oort Objects events on member changes,
        // and have this listener broadcast member changes to the clients of that node only.
        oort.observeChannel("/members/*");

        // We need a way to be notified when a user connects or disconnects from the cluster,
        // to make the user leave the rooms it joined. We cannot use Seti presence events,
        // because we would need UserInfo instances that are not carried by Seti presence events.
        oort.getBayeuxServer().addListener(this);
    }

    @PreDestroy
    private void destroy()
    {
        oort.getBayeuxServer().removeListener(this);
        oort.deobserveChannel("/members/*");
    }

    @Override
    public void sessionAdded(ServerSession session, ServerMessage message)
    {
        // Nothing to do
    }

    @Override
    public void sessionRemoved(ServerSession session, boolean expired)
    {
        UserInfo userInfo = usersService.getUserInfo(session);
        if (userInfo != null)
        {
            for (Map.Entry<RoomInfo, OortList<UserInfo>> roomMembers : roomToMembers.entrySet())
                leave(roomMembers.getValue(), roomMembers.getKey(), userInfo);
        }
    }

    public boolean join(RoomInfo roomInfo, UserInfo userInfo)
    {
        OortList<UserInfo> roomMembers = roomToMembers.get(roomInfo);
        if (roomMembers != null)
        {
            // We have a shared members list, update it
            if (roomMembers.addAndShare(userInfo))
            {
                logger.debug("{} joined {}", userInfo, roomInfo);
                // Broadcast the change to all clients of all nodes
                broadcastMembers(roomInfo, userInfo, "join");
                return true;
            }
        }
        return false;
    }

    public boolean leave(RoomInfo roomInfo, UserInfo userInfo)
    {
        OortList<UserInfo> roomMembers = roomToMembers.get(roomInfo);
        return roomMembers != null && leave(roomMembers, roomInfo, userInfo);
    }

    private boolean leave(OortList<UserInfo> roomMembers, RoomInfo roomInfo, UserInfo userInfo)
    {
        if (roomMembers.removeAndShare(userInfo))
        {
            logger.debug("{} left {}", userInfo, roomInfo);
            // Broadcast the change to all clients of all nodes
            broadcastMembers(roomInfo, userInfo, "leave");
            return true;
        }
        return false;
    }

    public void roomAdded(RoomInfo roomInfo)
    {
        String name = "members_room_" + roomInfo.getId();
        OortList<UserInfo> roomMembers = new OortList<>(oort, name, OortObjectFactories.<UserInfo>forConcurrentList());
        if (roomToMembers.putIfAbsent(roomInfo, roomMembers) == null)
        {
            oort.getBayeuxServer().createChannelIfAbsent(getChannel(roomInfo), new ConfigurableServerChannel.Initializer.Persistent());
            startMembers(roomMembers);
            logger.debug("Constructed room members for {}", roomInfo);
        }
    }

    private void startMembers(OortList<UserInfo> roomMembers)
    {
        try
        {
            roomMembers.start();
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    public void roomRemoved(RoomInfo roomInfo)
    {
        OortList<UserInfo> roomMembers = roomToMembers.remove(roomInfo);
        if (roomMembers != null)
        {
            stopMembers(roomMembers);
            oort.getBayeuxServer().getChannel(getChannel(roomInfo)).setPersistent(false);
            logger.debug("Destroyed room members for {}", roomInfo);
        }
    }

    private void stopMembers(OortList<UserInfo> roomMembers)
    {
        try
        {
            roomMembers.stop();
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    private String getChannel(RoomInfo roomInfo)
    {
        return "/members/" + roomInfo.getId();
    }

    private void broadcastMembers(RoomInfo roomInfo, UserInfo userInfo, String action)
    {
        logger.debug("Broadcast member {}: {} on {}", action, userInfo, roomInfo);
        Map<String, Object> data = new HashMap<>(2);
        data.put("action", action);
        data.put("members", Arrays.asList(userInfo));
        oort.getBayeuxServer().getChannel(getChannel(roomInfo)).publish(session, data);
    }

    public void deliverMembers(ServerSession session, UserInfo userInfo, RoomInfo roomInfo)
    {
        OortList<UserInfo> roomMembers = roomToMembers.get(roomInfo);
        if (roomMembers != null)
        {
            List<UserInfo> members = roomMembers.merge(OortObjectMergers.<UserInfo>listUnion());
            logger.debug("Delivering members to {}: {} on {}", userInfo, members, roomInfo);
            Map<String, Object> data = new HashMap<>(2);
            data.put("action", "join");
            data.put("members", members);
            session.deliver(this.session, getChannel(roomInfo), data);
        }
    }

    public boolean isMember(RoomInfo roomInfo, UserInfo userInfo)
    {
        OortList<UserInfo> roomMembers = roomToMembers.get(roomInfo);
        return roomMembers != null && roomMembers.isPresent(userInfo);
    }
}
