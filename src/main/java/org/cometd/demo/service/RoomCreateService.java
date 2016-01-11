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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.cometd.annotation.Listener;
import org.cometd.annotation.Service;
import org.cometd.annotation.Session;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.demo.Node;
import org.cometd.demo.model.Membership;
import org.cometd.demo.model.RoomInfo;
import org.cometd.oort.Oort;
import org.cometd.oort.OortMasterLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RoomCreateService} performs the actions needed to create a new chat room, owned by the local node.
 * <p />
 * {@link RoomInfo} is characterized by a unique across the cluster room {@code id}. In order to create
 * unique room {@code id}s, this service makes use of an {@link OortMasterLong} as id generator, which is
 * bootstrapped by reading a node-specific file that marks the node that can read it as the "master" node
 * for the id generator.
 */
@Service(RoomCreateService.NAME)
public class RoomCreateService
{
    public static final String NAME = "room_create";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final OortMasterLong roomIds;
    private final RoomsService roomsService;
    @Session
    private LocalSession session;

    public RoomCreateService(Oort oort, Node node, RoomsService roomsService) throws IOException
    {
        this.roomsService = roomsService;
        String name = "room_ids";
        boolean master = false;
        long initial = 0;
        InputStream input = getClass().getClassLoader().getResourceAsStream(name + "-" + node.getId() + ".properties");
        if (input != null)
        {
            try (InputStream stream = input)
            {
                Properties properties = new Properties();
                properties.load(stream);
                master = true;
                initial = Long.parseLong(properties.getProperty("value"));
            }
        }
        roomIds = new OortMasterLong(oort, name, master, initial);
    }

    @PostConstruct
    private void construct() throws Exception
    {
        roomIds.start();
    }

    @PreDestroy
    private void destroy() throws Exception
    {
        roomIds.stop();
    }

    @Listener("/service/room/create")
    public void createRoom(final ServerSession remote, final ServerMessage message)
    {
        Map<String,Object> data = message.getDataAsMap();
        final String roomName = (String)data.get("roomName");
        roomIds.addAndGet(1, new OortMasterLong.Callback()
        {
            @Override
            public void succeeded(Long result)
            {
                RoomInfo roomInfo = new RoomInfo(result, roomName, Membership.BRONZE);
                logger.debug("Creating room {}", roomInfo);
                roomsService.createRoomInfo(roomInfo);
                remote.deliver(session, message.getChannel(), roomInfo);
                roomsService.broadcastRooms();
            }

            @Override
            public void failed(Object failure)
            {
                remote.deliver(session, "/service/status", String.valueOf(failure));
            }
        });
    }
}
