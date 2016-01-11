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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.cometd.annotation.Configure;
import org.cometd.annotation.Service;
import org.cometd.annotation.Session;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ConfigurableServerChannel;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.demo.Node;
import org.cometd.demo.model.RoomInfo;
import org.cometd.demo.model.UserInfo;
import org.cometd.oort.Oort;
import org.cometd.oort.OortMap;
import org.cometd.oort.OortObject;
import org.cometd.oort.OortObjectFactories;
import org.cometd.oort.OortObjectMergers;
import org.cometd.oort.OortStringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RoomsService} maintains a shared {@link OortMap} of {@link RoomInfo}s so that all nodes have
 * all the {@link RoomInfo}s for all rooms.
 * <p />
 * This service is also responsible for pushing the rooms list to newly connected users, and it does
 * so by registering itself as a {@link BayeuxServer.SessionListener}.
 * <p />
 * Every time a room is created/removed  in a node, it is added to the {@link OortMap} and shared
 * across all nodes. Every {@link RoomsService} on every node listens for room added/removed events,
 * and collaborates with the {@link RoomMembersService} to maintain the room's members list.
 * <p />
 * Every time a room is created/removed in a node, this service needs to broadcast the new room list
 * to all clients in all nodes.
 * There are two ways of doing this:
 * <ul>
 * <li>
 *     have each {@link RoomsService} register itself as an {@link OortMap.EntryListener}, so that
 *     room list changes are broadcast across nodes via {@link OortMap} features, and then have
 *     each node broadcast the whole room list to locally connected users via a standard
 *     {@link ServerChannel#publish(org.cometd.bayeux.Session, Object)}
 * </li>
 * <li>
 *     have each {@link RoomsService} broadcast, upon changes, the whole room list across nodes
 *     via standard {@link Oort} features, in particular by having the room list channel observed
 *     by all nodes via {@link Oort#observeChannel(String)}.
 * </li>
 * </ul>
 * The former solution has been chosen for {@link RoomsService} to allow it to interact in a simpler
 * way with {@link RoomMembersService}.
 * <p />
 * {@link RoomMembersService} implements the latter solution.
 */
@Service(RoomsService.NAME)
public class RoomsService implements BayeuxServer.SessionListener, OortMap.EntryListener<String, RoomInfo>
{
    public static final String NAME = "rooms";
    private static final String CHANNEL = "/rooms";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Oort oort;
    private final Node node;
    private final UsersService usersService;
    private final RoomMembersService membersService;
    @Session
    private LocalSession session;
    private OortStringMap<RoomInfo> roomInfos;

    public RoomsService(Oort oort, Node node, UsersService usersService, RoomMembersService membersService)
    {
        this.oort = oort;
        this.node = node;
        this.usersService = usersService;
        this.membersService = membersService;
    }

    @Configure(CHANNEL)
    private void configureRoomsChannel(ConfigurableServerChannel channel)
    {
        channel.setPersistent(true);
    }

    @PostConstruct
    private void construct() throws Exception
    {
        roomInfos = new OortStringMap<>(oort, NAME, OortObjectFactories.<String, RoomInfo>forConcurrentMap());
        roomInfos.start();
        roomInfos.addListener(new OortMap.DeltaListener<>(roomInfos));
        roomInfos.addEntryListener(this);

        List<RoomInfo> chatRooms = loadRooms();
        logger.debug("Sharing Rooms: {}", chatRooms);
        for (RoomInfo roomInfo : chatRooms)
            roomInfos.putAndShare(String.valueOf(roomInfo.getId()), roomInfo);
        broadcastRooms();

        oort.getBayeuxServer().addListener(this);
    }

    @PreDestroy
    private void destroy() throws Exception
    {
        oort.getBayeuxServer().removeListener(this);
        roomInfos.removeEntryListener(this);
        roomInfos.stop();
    }

    public String findOortURLFor(long roomId)
    {
        OortObject.Info<ConcurrentMap<String, RoomInfo>> info = roomInfos.findInfo(String.valueOf(roomId));
        return info == null ? null : info.getOortURL();
    }

    public RoomInfo findRoomInfo(long roomId)
    {
        return roomInfos.find(String.valueOf(roomId));
    }

    public RoomInfo getRoomInfo(long roomId)
    {
        return roomInfos.get(String.valueOf(roomId));
    }

    public RoomInfo replaceRoomInfo(RoomInfo roomInfo)
    {
        return roomInfos.putAndShare(String.valueOf(roomInfo.getId()), roomInfo);
    }

    public void createRoomInfo(RoomInfo roomInfo)
    {
        roomInfos.putAndShare(String.valueOf(roomInfo.getId()), roomInfo);
    }

    @Override
    public void sessionAdded(ServerSession remote, ServerMessage message)
    {
        // New user, deliver rooms
        deliverRooms(remote);
    }

    @Override
    public void sessionRemoved(ServerSession serverSession, boolean expired)
    {
    }

    @Override
    public void onPut(OortObject.Info<ConcurrentMap<String, RoomInfo>> info, OortMap.Entry<String, RoomInfo> entry)
    {
        // Update rooms members
        membersService.roomAdded(entry.getNewValue());
        if (!info.isLocal())
            broadcastRooms();
    }

    @Override
    public void onRemoved(OortObject.Info<ConcurrentMap<String, RoomInfo>> info, OortMap.Entry<String, RoomInfo> entry)
    {
        // Update rooms members
        membersService.roomRemoved(entry.getOldValue());
        if (!info.isLocal())
            broadcastRooms();
    }

    private void deliverRooms(ServerSession remote)
    {
        UserInfo userInfo = usersService.getUserInfo(remote);
        if (userInfo != null)
        {
            Collection<RoomInfo> rooms = roomInfos.merge(OortObjectMergers.<String, RoomInfo>concurrentMapUnion()).values();
            logger.debug("Delivering rooms to user '{}': {}", userInfo.getId(), rooms);
            remote.deliver(session, CHANNEL, rooms);
        }
    }

    protected void broadcastRooms()
    {
        Collection<RoomInfo> rooms = roomInfos.merge(OortObjectMergers.<String, RoomInfo>concurrentMapUnion()).values();
        logger.debug("Broadcasting rooms {}", rooms);
        oort.getBayeuxServer().getChannel(CHANNEL).publish(session, rooms);
    }

    @SuppressWarnings("unchecked")
    private List<RoomInfo> loadRooms() throws IOException
    {
        String fileName = "rooms-" + node.getId() + ".json";
        InputStream stream = getClass().getClassLoader().getResourceAsStream(fileName);
        if (stream == null)
            throw new FileNotFoundException(fileName);

        try (Reader reader = new InputStreamReader(stream, "UTF-8"))
        {
            Object result = oort.getJSONContextClient().getParser().parse(reader, Object.class);
            if (result instanceof List)
                return (List<RoomInfo>)result;
            if (result instanceof Object[])
            {
                List<RoomInfo> list = new ArrayList<>();
                for (Object room : (Object[])result)
                    list.add((RoomInfo)room);
                return list;
            }
            return Collections.singletonList((RoomInfo)result);
        }
        catch (ParseException x)
        {
            throw new IOException(x);
        }
    }
}
