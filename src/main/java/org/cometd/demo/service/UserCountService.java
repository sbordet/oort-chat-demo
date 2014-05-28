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

import org.cometd.annotation.Listener;
import org.cometd.annotation.Service;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ConfigurableServerChannel;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.oort.Oort;
import org.cometd.oort.OortLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link UserCountService} maintains the total number of connected users across the cluster.
 * <p />
 * It makes use of {@link OortLong}, which keeps the number of connected users per node, and
 * then uses {@link OortLong#sum()} to compute the total number of users connected to all nodes.
 * <p />
 * {@link UserCountService} register itself as a {@link BayeuxServer.SessionListener} to be
 * notified when users disconnect.
 * It relies on a message sent by each remote user upon successful login to increase the user
 * count, instead of {@link #sessionAdded(ServerSession,ServerMessage)}, because the session
 * added event is fired too early (see comments there).
 */
@Service(UserCountService.NAME)
public class UserCountService implements BayeuxServer.SessionListener
{
    public static final String NAME = "user_count";
    private static final String CHANNEL = "/users";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final OortLong counter;

    public UserCountService(Oort oort)
    {
        this.counter = new OortLong(oort, NAME);
    }

    @PostConstruct
    public void construct() throws Exception
    {
        counter.start();
        Oort oort = counter.getOort();
        BayeuxServer bayeuxServer = oort.getBayeuxServer();
        bayeuxServer.addListener(this);
        bayeuxServer.createChannelIfAbsent(CHANNEL, new ConfigurableServerChannel.Initializer.Persistent());
        oort.observeChannel(CHANNEL);
    }

    @PreDestroy
    public void destroy() throws Exception
    {
        Oort oort = counter.getOort();
        oort.deobserveChannel(CHANNEL);
        BayeuxServer bayeuxServer = oort.getBayeuxServer();
        bayeuxServer.getChannel(CHANNEL).setPersistent(false);
        bayeuxServer.removeListener(this);
        counter.stop();
    }

    @Override
    public void sessionAdded(ServerSession session, ServerMessage message)
    {
        // Connections from other Oort nodes will trigger this callback and
        // cannot be distinguished from remote user sessions.
        // Furthermore, Oort.isOort(session) called from here will return
        // false even if it is a connection from another Oort node, because
        // this event fires very early and Oort is not completely setup yet.
        // Furthermore, remote user sessions will not be subscribed yet, so
        // any message broadcast from here will be lost by this remote user.
        // Therefore, we rely on a /service/init message to make sure it is a
        // real remote user, and already subscribed to receive user count updates.
    }

    @Override
    public void sessionRemoved(ServerSession session, boolean expired)
    {
        // We want to count only real remote users
        if (session.isLocalSession() || counter.getOort().isOort(session))
            return;
        counter.addAndGet(-1);
        broadcastUserCount();
    }

    @Listener("/service/init")
    public void process(final ServerSession remote, ServerMessage message)
    {
        counter.addAndGet(1);
        broadcastUserCount();
    }

    private void broadcastUserCount()
    {
        long result = counter.sum();
        logger.debug("Broadcasting user count {}", result);
        counter.getOort().getBayeuxServer().getChannel(CHANNEL).publish(counter.getLocalSession(), result);
    }
}
