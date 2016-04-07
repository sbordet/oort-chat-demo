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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.cometd.annotation.Service;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.demo.model.UserInfo;
import org.cometd.oort.OortMap;
import org.cometd.oort.OortObjectFactories;
import org.cometd.oort.OortStringMap;
import org.cometd.oort.Seti;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link UsersService} maintains a shared {@link OortMap} of {@link UserInfo}s, so that all nodes have
 * all the {@link UserInfo}s of all users.
 * <p />
 * In order to maintain this {@link OortMap}, it register itself as a {@link BayeuxServer.SessionListener}
 * so that it is notified every time a new session is created/destroyed on the local node.
 * <p />
 * This service does not directly interacts with remote clients, but it is used by other services.
 */
@Service(UsersService.NAME)
public class UsersService implements BayeuxServer.SessionListener
{
    public static final String NAME = "users";
    public static final String USER_INFO = UsersService.class.getName() + ".userInfo";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ConcurrentMap<String, ServerSession> userToSession = new ConcurrentHashMap<>();
    private final Seti seti;
    private OortStringMap<UserInfo> userInfos;

    public UsersService(Seti seti)
    {
        this.seti = seti;
    }

    @PostConstruct
    public void construct() throws Exception
    {
        userInfos = new OortStringMap<>(seti.getOort(), NAME, OortObjectFactories.<String, UserInfo>forConcurrentMap());
        userInfos.start();
        seti.getOort().getBayeuxServer().addListener(this);
    }

    @PreDestroy
    public void destroy() throws Exception
    {
        seti.getOort().getBayeuxServer().removeListener(this);
        userInfos.stop();
    }

    @Override
    public void sessionAdded(ServerSession session, ServerMessage message)
    {
        UserInfo userInfo = (UserInfo)session.getAttribute(USER_INFO);
        if (userInfo != null)
        {
            String userId = userInfo.getId();
            logger.debug("Logged in user '{}'@{}", userId, session.getId());
            if (userToSession.putIfAbsent(userId, session) == null)
            {
                // Associate the new session with Seti for peer-to-peer communication.
                // Not strictly needed if the chat does not offer peer-to-peer chat features.
                seti.associate(userId, session);
                // Track and share with other nodes the new session on this local node.
                userInfos.putAndShare(userId, userInfo);
            }
        }
    }

    @Override
    public void sessionRemoved(ServerSession session, boolean expired)
    {
        UserInfo userInfo = (UserInfo)session.getAttribute(USER_INFO);
        if (userInfo != null)
        {
            String userId = userInfo.getId();
            logger.debug("{} user '{}'@{}", expired ? "Expired" : "Logged out", userId, session.getId());
            userInfos.removeAndShare(userId);
            seti.disassociate(userId, session);
            userToSession.remove(userId);
        }
    }

    public UserInfo find(String userId)
    {
        return userInfos.find(userId);
    }

    public UserInfo getUserInfo(ServerSession session)
    {
        return (UserInfo)session.getAttribute(USER_INFO);
    }
}
