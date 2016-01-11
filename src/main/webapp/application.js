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

require({
        baseUrl: 'jquery',
        paths: {
            jquery: 'jquery-2.1.4',
            org: '../org'
        }
    },
    ['jquery', 'jquery.cometd'],
    function($, cometd)
    {
        $(document).ready(function()
        {
            var path = location.pathname;
            var contextPath = path.substring(0, path.lastIndexOf('/'));
            var chat = new Chat(contextPath);

            // Setup UI

            var userField = $('#user-input');
            $('#logon').show();
            $('#login').on('click', function()
            {
                chat.login(userField.val());
            });
            userField.focus();

            $('#message').hide();
            $('#logout').on('click', chat.logout);

            var chatField = $('#chat-text');
            chatField.on('keyup', function(e)
            {
                if (e.keyCode === 13)
                {
                    chat.sendText(chatField.val());
                    chatField.val('');
                }
            });

            window.onbeforeunload = chat.logout;
            $(window).unload(chat.logout);

            /* Initialize CometD */
            var cometURL = location.protocol + '//' + location.host + contextPath + '/cometd';
            cometd.configure({
                url: cometURL,
                logLevel: 'info'
            });
            cometd.addListener('/meta/handshake', function(message)
            {
                if (message.successful)
                {
                    cometd._info('Logged in user', chat.user);
                    cometd.batch(function()
                    {
                        cometd.subscribe('/users', chat.onUsers);
                        cometd.subscribe('/rooms', chat.onRooms);
                        cometd.subscribe('/service/room/join', chat.onRoomJoined);
                        cometd.subscribe('/service/room/leave', chat.onRoomLeft);
                        cometd.subscribe('/service/room/edit', chat.onRoomEdit);
                        cometd.subscribe('/service/room/create', chat.onRoomCreate);
                        cometd.subscribe('/service/chat', chat.onChatHistory);
                        cometd.subscribe('/service/status', chat.onStatus);
                        chat.resubscribe();
                        cometd.publish('/service/init', {});
                    });
                }
            });
        });

        function Chat(contextPath)
        {
            var _self = this;
            var _userId;
            var _rooms;
            var _room;
            var _members;
            var _membersSubscription;
            var _chatSubscription;

            function _uiSetNewRoom()
            {
                $('#rooms-header').empty().append($('<img src="' + contextPath + '/images/add.svg" title="New Room" />')
                    .on('click', function()
                    {
                        _self.newRoom();
                    }));
            }

            this.login = function(user)
            {
                if (!user)
                {
                    alert('Please enter a user name');
                    return;
                }

                _userId = user;
                this.user = user;

                $('#logon').hide();
                $('#message').show();
                $('#user').text(user + ':');
                $('#chat-text').attr('disabled', true);
                $('#status').text('Choose a room to join');
                _uiSetNewRoom();

                cometd.handshake({
                    ext: {
                        auth: {
                            user: _userId
                        }
                    }
                });
            };

            this.logout = function()
            {
                cometd.disconnect();
                cometd._info('Logged out user', _userId);

                _userId = undefined;
                _rooms = undefined;
                _room = undefined;
                _members = undefined;
                _membersSubscription = undefined;
                _chatSubscription = undefined;

                $('#message').hide();
                $('#rooms-list').empty();
                $('#members').empty();
                $('#chat-history').empty();
                $('#status').empty();
                $('#logon').show();
                $('#user-input').val('').focus();
            };

            this.resubscribe = function()
            {
                if (_membersSubscription)
                {
                    _membersSubscription = cometd.resubscribe(_membersSubscription);
                }
                if (_chatSubscription)
                {
                    _chatSubscription = cometd.resubscribe(_chatSubscription);
                }
            };

            function _unsubscribe()
            {
                if (_membersSubscription)
                {
                    cometd.unsubscribe(_membersSubscription);
                    _membersSubscription = undefined;
                }
                if (_chatSubscription)
                {
                    cometd.unsubscribe(_chatSubscription);
                    _chatSubscription = undefined;
                }
            }

            this.joinRoom = function(room)
            {
                cometd.batch(this, function()
                {
                    _self.leaveRoom();
                    cometd._info('Joining room', room.name);
                    // Subscribe to the members list of this room
                    _membersSubscription = cometd.subscribe('/members/' + room.id, _self.onMembers);
                    // Subscribe to the chats of this room
                    _chatSubscription = cometd.subscribe('/chat/' + room.id, _self.onChat);
                    cometd.publish('/service/room/join', {
                        roomId: room.id
                    });
                });
            };

            this.leaveRoom = function()
            {
                if (_room)
                {
                    $('#chat-text').attr('disabled', true);
                    cometd.batch(this, function()
                    {
                        cometd._info('Leaving room', _room.name);
                        cometd.publish('/service/room/leave', {
                            roomId: _room.id
                        });
                        // Unsubscribe from this room
                        _unsubscribe();
                    });
                }
            };

            this.newRoom = function()
            {
                var roomsHeader = $('#rooms-header');
                roomsHeader.empty()
                    .append($('<input type="text" value="" />'))
                    .append($('<button type="button">Save</button>').on('click', function()
                    {
                        _self.createRoom(roomsHeader.find('input:first').val());
                    }))
                    .append($('<button type="button">Cancel</button>').on('click', function()
                    {
                        _uiSetNewRoom();
                    }));
            };

            this.createRoom = function(roomName)
            {
                if (!roomName)
                {
                    alert('Please enter a room name');
                    return;
                }
                cometd._info('Creating room', roomName);
                cometd.publish('/service/room/create', {
                    roomName: roomName
                });
            };

            this.editRoom = function(room)
            {
                var roomElement = $('#room-name');
                roomElement.empty()
                    .append($('<input type="text" value="' + room.name + '" />'))
                    .append($('<button type="button">Save</button>').on('click', function()
                    {
                        _self.saveRoom(room, roomElement.find('input:first').val());
                    }))
                    .append($('<button type="button">Cancel</button>').on('click', function()
                    {
                        _uiSetRoomName(room);
                    }));
            };

            this.saveRoom = function(room, newName)
            {
                cometd._info('Saving room', room.name, '->', newName);
                cometd.publish('/service/room/edit', {
                    roomId: room.id,
                    roomName: newName
                });
            };

            this.sendText = function(text)
            {
                if (text)
                {
                    $.cometd.publish('/service/chat', {
                        userId: _userId,
                        roomId: _room.id,
                        text: text
                    });
                }
            };

            function _scrollDown()
            {
                var chat = $('#chat-history');
                chat.scrollTop(chat.prop('scrollHeight') - chat.outerHeight());
            }

            function _uiForChatLine(data)
            {
                return $('' +
                    '<span>' +
                    '    <span class="author">' + data.user.id + ':&nbsp;</span>' +
                    '    <span class="text">' + data.text + '</span>' +
                    '</span>' +
                    '<br />')
            }

            this.onChat = function(message)
            {
                $('#chat-history').append(_uiForChatLine(message.data));
                _scrollDown();
            };

            this.onChatHistory = function(message)
            {
                var history = message.data;
                cometd._info('Chat history', history);
                var chat = $('#chat-history');
                chat.empty();
                $.each(history.chats, function(i, item)
                {
                    chat.append(_uiForChatLine(item));
                });
                _scrollDown();
            };

            this.onMembers = function(message)
            {
                var data = message.data;
                cometd._info('Members', data);
                _members = _members || {};
                if ('join' === data.action)
                {
                    $.each(data.members, function(i, member)
                    {
                        _members[member.id] = member;
                    });
                }
                else if ('leave' === data.action)
                {
                    $.each(data.members, function(i, member)
                    {
                        delete _members[member.id];
                    });
                }

                var memberNames = $.map(_members, function(member, i)
                {
                    return member.id;
                });
                $('#members').empty();
                $.each(memberNames.sort(), function(i, memberName)
                {
                    $('#members').append($('<div><span>' + memberName + '</span></div>'));
                });
            };

            function _uiSetRoomName(room)
            {
                $('#room-name')
                    .empty()
                    .append($('<img src="' + contextPath + '/images/edit.svg" title="Edit Room" />')
                        .click(function()
                        {
                            _self.editRoom(room);
                        }))
                    .append($('<span>' + room.name + '</span>'));
            }

            function _imgForSelectedRoom()
            {
                return $('<img src="' + contextPath + '/images/exit.svg" title="Leave Room" />')
                    .click(function()
                    {
                        _self.leaveRoom();
                    });
            }

            this.onRoomJoined = function(message)
            {
                _room = message.data;
                cometd._info('Room joined', _room);

                _uiSetRoomName(_room);
                $('#chat-text').removeAttr('disabled').focus();
                $('#status').text('Joined room \'' + _room.name + '\'');
                $.each(_rooms, function(i, room)
                {
                    if (_room.id == room.id)
                    {
                        var roomElement = $('#room_' + room.id);
                        roomElement.addClass('selected');
                        roomElement.find('img:first').replaceWith(_imgForSelectedRoom());
                    }
                });
            };

            this.onRoomLeft = function(message)
            {
                var room = message.data;
                cometd._info('Room left', room);

                if (_room.id == room.id)
                {
                    $('#room-name').text('');
                    $('#members').empty();
                    $('#chat-history').empty();
                    $('#status').text('Left room \'' + room.name + '\'');
                    _room = undefined;
                }

                $.each(_rooms, function(i, item)
                {
                    if (room.id == item.id)
                    {
                        var roomElement = $('#room_' + room.id);
                        roomElement.removeClass('selected');
                        roomElement.find('img:first').replaceWith(_imgForNonSelectedRoom(room));
                    }
                });
            };

            this.onRoomEdit = function(message)
            {
                var oldRoom = _room;
                _room = message.data;
                cometd._info('Room edited', _room);

                _uiSetRoomName(_room);
                $('#status').text('Room name \'' + oldRoom.name + '\' => \'' + _room.name + '\'');
                $.each(_rooms, function(i, room)
                {
                    if (_room.id == room.id)
                    {
                        var roomElement = $('#room_' + room.id);
                        roomElement.find('span:first').text(room.name);
                    }
                });
            };

            this.onRoomCreate = function(message)
            {
                var room = message.data;
                cometd._info('Room created', room);
                _uiSetNewRoom();
            };

            function _imgForNonSelectedRoom(room)
            {
                return $('<img src="' + contextPath + '/images/enter.svg" title="Join Room" />')
                    .click(function()
                    {
                        _self.joinRoom(room);
                    });
            }

            this.onRooms = function(message)
            {
                _rooms = message.data;
                cometd._info('Rooms updated', _rooms);
                $('#rooms-list').empty();
                $.each(_rooms, function(i, room)
                {
                    var line = $('<div id="room_' + room.id + '"/>');
                    var selected = _room && _room.id === room.id;
                    if (selected)
                    {
                        _room = room;
                        line.addClass('selected');
                        line.append(_imgForSelectedRoom(room));
                        _uiSetRoomName(room);
                    }
                    else
                    {
                        line.append(_imgForNonSelectedRoom(room));
                    }
                    line.append($('<span>' + room.name + '</span>'));
                    $('#rooms-list').append(line);
                });
            };

            this.onUsers = function(message)
            {
                var count = message.data;
                cometd._info('Users count updated to', count);
                $('#user-count').text('Total Users: ' + count);
            };

            this.onStatus = function(message)
            {
                $('#status').text(message.data);
            };
        }
    }
);
