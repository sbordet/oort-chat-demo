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

package org.cometd.demo;

import java.io.IOException;
import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.UnavailableException;

import org.cometd.annotation.ServerAnnotationProcessor;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.demo.service.ChatHistoryArchiveService;
import org.cometd.demo.service.ChatHistoryRequestService;
import org.cometd.demo.service.ChatHistoryService;
import org.cometd.demo.service.ChatService;
import org.cometd.demo.service.RoomCreateService;
import org.cometd.demo.service.RoomEditService;
import org.cometd.demo.service.RoomJoinService;
import org.cometd.demo.service.RoomLeaveService;
import org.cometd.demo.service.RoomMembersService;
import org.cometd.demo.service.RoomsService;
import org.cometd.demo.service.UserCountService;
import org.cometd.demo.service.UsersService;
import org.cometd.oort.Oort;
import org.cometd.oort.Seti;

/**
 * Startup servlet that instantiates and initializes services.
 */
public class StartupServlet extends GenericServlet
{
    @Override
    public void init() throws ServletException
    {
        try
        {
            Seti seti = (Seti)getServletContext().getAttribute(Seti.SETI_ATTRIBUTE);
            Oort oort = seti.getOort();
            BayeuxServer bayeuxServer = oort.getBayeuxServer();

            Node node = new Node(getInitParameter("node"));

            bayeuxServer.setSecurityPolicy(new SecurityPolicy(oort));

            // Services here are instantiated using constructor dependency injection instead of @Inject.
            // This guarantees that services are initialized in the right order, avoiding that a
            // service B that depends on another service A is initialized before service A.
            // Instead of using CometD's annotation servlet (that can only instantiate parameterless services)
            // we create and use a ServerAnnotationProcessor manually.
            ServerAnnotationProcessor processor = new ServerAnnotationProcessor(bayeuxServer, oort, seti);
            UserCountService userCountService = new UserCountService(oort);
            processor.process(userCountService);
            UsersService usersService = new UsersService(seti);
            processor.process(usersService);
            RoomMembersService membersService = new RoomMembersService(oort, usersService);
            processor.process(membersService);
            RoomsService roomsService = new RoomsService(oort, node, usersService, membersService);
            processor.process(roomsService);
            ChatHistoryService chatHistoryService = new ChatHistoryService(5);
            processor.process(chatHistoryService);
            ChatHistoryArchiveService chatHistoryArchiveService = new ChatHistoryArchiveService(oort, roomsService, chatHistoryService);
            processor.process(chatHistoryArchiveService);
            ChatHistoryRequestService chatHistoryRequestService = new ChatHistoryRequestService(oort, usersService, roomsService, chatHistoryService);
            processor.process(chatHistoryRequestService);
            RoomJoinService roomJoinService = new RoomJoinService(usersService, roomsService, membersService, chatHistoryRequestService);
            processor.process(roomJoinService);
            RoomLeaveService roomLeaveService = new RoomLeaveService(usersService, roomsService, membersService);
            processor.process(roomLeaveService);
            RoomEditService roomEditService = new RoomEditService(oort, usersService, roomsService, membersService);
            processor.process(roomEditService);
            RoomCreateService roomCreateService = new RoomCreateService(oort, node, roomsService);
            processor.process(roomCreateService);
            ChatService chatService = new ChatService(oort, usersService, roomsService, chatHistoryArchiveService);
            processor.process(chatService);
        }
        catch (IOException x)
        {
            throw new ServletException(x);
        }
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException
    {
        throw new UnavailableException("Configuration Servlet");
    }
}
