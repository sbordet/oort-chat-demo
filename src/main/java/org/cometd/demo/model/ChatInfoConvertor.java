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

package org.cometd.demo.model;

import java.util.Map;

import org.eclipse.jetty.util.ajax.JSON;

public class ChatInfoConvertor implements JSON.Convertor
{
    @Override
    public void toJSON(Object obj, JSON.Output out)
    {
        ChatInfo chatInfo = (ChatInfo)obj;
        out.addClass(ChatInfo.class);
        out.add("user", chatInfo.getUserInfo());
        out.add("text", chatInfo.getText());
    }

    @Override
    public Object fromJSON(Map object)
    {
        UserInfo userInfo = (UserInfo)object.get("user");
        String text = (String)object.get("text");
        return new ChatInfo(userInfo, text);
    }
}
