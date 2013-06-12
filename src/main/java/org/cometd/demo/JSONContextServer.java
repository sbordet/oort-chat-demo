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

import org.cometd.common.JSONContext;
import org.cometd.demo.model.ChatHistoryInfo;
import org.cometd.demo.model.ChatHistoryInfoConvertor;
import org.cometd.demo.model.ChatInfo;
import org.cometd.demo.model.ChatInfoConvertor;
import org.cometd.demo.model.Membership;
import org.cometd.demo.model.RoomChatInfo;
import org.cometd.demo.model.RoomChatInfoConvertor;
import org.cometd.demo.model.RoomInfo;
import org.cometd.demo.model.RoomInfoConvertor;
import org.cometd.demo.model.UserInfo;
import org.cometd.demo.model.UserInfoConvertor;
import org.cometd.server.JettyJSONContextServer;
import org.eclipse.jetty.util.ajax.JSONEnumConvertor;

/**
 * Customization of {@link JSONContext.Server} to allow transparent replication
 * of {@code *Info} objects across the nodes of the cluster.
 */
public class JSONContextServer extends JettyJSONContextServer
{
    public JSONContextServer()
    {
        getJSON().addConvertor(Membership.class, new JSONEnumConvertor(true));
        getJSON().addConvertor(RoomInfo.class, new RoomInfoConvertor());
        getJSON().addConvertor(UserInfo.class, new UserInfoConvertor());
        getJSON().addConvertor(ChatInfo.class, new ChatInfoConvertor());
        getJSON().addConvertor(RoomChatInfo.class, new RoomChatInfoConvertor());
        getJSON().addConvertor(ChatHistoryInfo.class, new ChatHistoryInfoConvertor());
    }
}
