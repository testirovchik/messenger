import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { jwtDecode } from 'jwt-decode';
import './ChatRoom.css';

const ChatRoom = () => {
    const { chatId } = useParams();
    const navigate = useNavigate();
    const [messages, setMessages] = useState([]);
    const [messageInput, setMessageInput] = useState('');
    const [myUserId, setMyUserId] = useState(null);
    const [ws, setWs] = useState(null);
    const messagesEndRef = useRef(null);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    useEffect(() => {
        const token = localStorage.getItem('chat_token');
        if (!token) {
            navigate('/login');
            return;
        }

        try {
            const decoded = jwtDecode(token);
            setMyUserId(decoded.sub);
        } catch (err) {
            console.error('Invalid token', err);
            navigate('/login');
            return;
        }

        const fetchHistory = async () => {
            try {
                const response = await fetch(`http://localhost:8081/api/messages/${chatId}`, {
                    headers: {
                        'Authorization': `Bearer ${token}`
                    }
                });
                if (response.ok) {
                    const data = await response.json();
                    setMessages(data);
                }
            } catch (err) {
                console.error('Error fetching history:', err);
            }
        };

        fetchHistory();

        const socket = new WebSocket("ws://localhost:8081/ws-chat");

        socket.onopen = () => {
            console.log("Connected to WebSocket");
            const decoded = jwtDecode(token);
            socket.send(JSON.stringify({ type: "REGISTER", userId: decoded.sub }));
        };

        socket.onmessage = (event) => {
            const message = JSON.parse(event.data);
            // Only add if it's for this chat
            if (String(message.chatId) === String(chatId)) {
                setMessages((prev) => [...prev, message]);
            }
        };

        socket.onclose = () => console.log("WebSocket closed");
        setWs(socket);

        return () => {
            if (socket) socket.close();
        };
    }, [chatId, navigate]);

    const sendMessage = () => {
        if (ws && ws.readyState === WebSocket.OPEN && messageInput.trim()) {
            const messagePayload = {
                type: "MESSAGE",
                chatId: chatId,
                content: messageInput,
                senderId: myUserId
            };

            ws.send(JSON.stringify(messagePayload));

            // Optimistic update
            setMessages((prev) => [...prev, {
                content: messageInput,
                senderId: myUserId,
                createdAt: new Date().toISOString()
            }]);
            setMessageInput('');
        }
    };

    return (
        <div className="chat-room-container">
            <div className="chat-header">
                <button className="back-button" onClick={() => navigate('/chats')}>
                    &larr;
                </button>
                <h3 className="chat-header-title">Chat #{chatId}</h3>
                <div style={{ width: '32px' }}></div>
            </div>

            <div className="chat-messages">
                {messages.map((msg, index) => {
                    const isMe = String(msg.senderId) === String(myUserId);
                    return (
                        <div key={index} className={`message-wrapper ${isMe ? 'me' : 'others'}`}>
                            <div className="message-bubble">
                                <div className="message-content">{msg.content}</div>
                                <div className="message-time">
                                    {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                                </div>
                            </div>
                        </div>
                    );
                })}
                <div ref={messagesEndRef} />
            </div>

            <div className="chat-input-area">
                <input
                    type="text"
                    placeholder="Type a message..."
                    value={messageInput}
                    onChange={(e) => setMessageInput(e.target.value)}
                    onKeyPress={(e) => e.key === 'Enter' && sendMessage()}
                />
                <button className="send-button" onClick={sendMessage}>
                    Send
                </button>
            </div>
        </div>
    );
};

export default ChatRoom;
