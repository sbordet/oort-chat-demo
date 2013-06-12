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

public class RoomInfoConvertor implements JSON.Convertor
{
    @Override
    public void toJSON(Object obj, JSON.Output out)
    {
        RoomInfo roomInfo = (RoomInfo)obj;
        out.addClass(RoomInfo.class);
        out.add("id", roomInfo.getId());
        out.add("name", roomInfo.getName());
        out.add("membership", roomInfo.getMembership());
    }

    @Override
    public Object fromJSON(Map object)
    {
        long id = ((Number)object.get("id")).longValue();
        String name = (String)object.get("name");
        Membership membership = (Membership)object.get("membership");
        return new RoomInfo(id, name, membership);
    }
}
