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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.cometd.annotation.Service;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.demo.model.ChatHistoryInfo;
import org.cometd.demo.model.RoomInfo;
import org.cometd.demo.model.UserInfo;
import org.cometd.oort.Oort;
import org.cometd.oort.OortService;

/**
 * {@link ChatHistoryRequestService} is responsible to send the request to retrive the last messages of a
 * chat room to the right node.
 * <p />
 * Chat messages are archived in the node that owns the room by an instance of {@link ChatHistoryService}.
 */
@Service(ChatHistoryRequestService.NAME)
public class ChatHistoryRequestService extends OortService<ChatHistoryInfo, OortService.ServerContext>
{
    public static final String NAME = "chat_history_request";

    private final UsersService usersService;
    private final RoomsService roomsService;
    private final ChatHistoryService chatHistoryService;

    public ChatHistoryRequestService(Oort oort, UsersService usersService, RoomsService roomsService, ChatHistoryService chatHistoryService)
    {
        super(oort, NAME);
        this.usersService = usersService;
        this.roomsService = roomsService;
        this.chatHistoryService = chatHistoryService;
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

    public void deliverChatHistory(ServerSession remote, RoomInfo roomInfo)
    {
        String oortURL = roomsService.findOortURLFor(roomInfo.getId());
        if (oortURL != null)
            forward(oortURL, roomInfo, new ServerContext(remote, null));
    }

    @Override
    protected Result<ChatHistoryInfo> onForward(Request request)
    {
        RoomInfo roomInfo = (RoomInfo)request.getData();
        return Result.success(chatHistoryService.retrieve(roomInfo));
    }

    @Override
    protected void onForwardSucceeded(ChatHistoryInfo result, ServerContext context)
    {
        ServerSession remote = context.getServerSession();
        UserInfo userInfo = usersService.getUserInfo(remote);
        if (userInfo != null)
        {
            logger.debug("Delivering chat history to {}: {}", userInfo, result);
            remote.deliver(getLocalSession(), "/service/chat", result);
        }
    }

    @Override
    protected void onForwardFailed(Object failure, ServerContext context)
    {
        // Nothing to do, the user will see an empty chat history on the UI
    }
}
