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

import java.util.Map;

import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.demo.model.Membership;
import org.cometd.demo.model.UserInfo;
import org.cometd.demo.service.UsersService;
import org.cometd.oort.Oort;
import org.cometd.server.DefaultSecurityPolicy;

/**
 * A CometD {@link org.cometd.bayeux.server.SecurityPolicy} that handles authentication.
 */
public class SecurityPolicy extends DefaultSecurityPolicy
{
    private final Oort oort;

    public SecurityPolicy(Oort oort)
    {
        this.oort = oort;
    }

    @Override
    public boolean canHandshake(BayeuxServer server, ServerSession session, ServerMessage message)
    {
        // Services can always handshake
        if (session.isLocalSession())
            return true;

        // Other oort nodes can always handshake (they must be configured with the same oort secret)
        if (oort.isOortHandshake(message))
            return true;

        // Remote users must authenticate
        return authenticate(session, message);
    }

    private boolean authenticate(ServerSession session, ServerMessage message)
    {
        Map<String,Object> ext = message.getExt();
        if (ext != null)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> authentication = (Map<String, Object>)ext.get("auth");
            if (authentication != null)
            {
                String userName = (String)authentication.get("user");
                if (userName != null)
                {
                    String[] parts = userName.split("/");
                    String userId = parts[0];
                    Membership membership = Membership.BRONZE;
                    if (parts.length > 1)
                        membership = Membership.valueOf(parts[1].toUpperCase());
                    UserInfo userInfo = new UserInfo(userId, membership);
                    session.setAttribute(UsersService.USER_INFO, userInfo);
                    return true;
                }
            }
        }
        return false;
    }
}
