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

public class RoomInfo
{
    private final long id;
    private final String name;
    private final Membership membership;

    public RoomInfo(long id, String name, Membership membership)
    {
        this.id = id;
        this.name = name;
        this.membership = membership;
    }

    public long getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public Membership getMembership()
    {
        return membership;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (!(obj instanceof RoomInfo)) return false;
        RoomInfo that = (RoomInfo)obj;
        return id == that.id;
    }

    @Override
    public int hashCode()
    {
        return (int)id;
    }

    @Override
    public String toString()
    {
        return String.format("%s[%d/%s]", getClass().getSimpleName(), getId(), getName());
    }
}
