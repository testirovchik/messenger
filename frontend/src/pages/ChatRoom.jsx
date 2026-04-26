import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { jwtDecode } from 'jwt-decode';

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
            setMessages((prev) => [...prev, message]);
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

            setMessages((prev) => [...prev, {
                content: messageInput,
                senderId: myUserId,
                createdAt: new Date().toISOString()
            }]);
            setMessageInput('');
        }
    };

    const containerStyle = {
        display: 'flex',
        flexDirection: 'column',
        height: '90vh',
        maxWidth: '800px',
        margin: '10px auto',
        border: '1px solid #ddd',
        borderRadius: '12px',
        overflow: 'hidden',
        backgroundColor: '#fff'
    };

    const headerStyle = {
        padding: '15px 20px',
        borderBottom: '1px solid #eee',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
    };

    const chatBoxStyle = {
        flex: 1,
        overflowY: 'auto',
        padding: '20px',
        display: 'flex',
        flexDirection: 'column',
        gap: '10px',
        backgroundColor: '#f0f2f5'
    };

    const messageWrapperStyle = (isMe) => ({
        display: 'flex',
        justifyContent: isMe ? 'flex-end' : 'flex-start'
    });

    const messageBubbleStyle = (isMe) => ({
        maxWidth: '70%',
        padding: '10px 15px',
        borderRadius: '18px',
        backgroundColor: isMe ? '#0084ff' : '#fff',
        color: isMe ? '#fff' : '#000',
        boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
        fontSize: '15px'
    });

    const timeStyle = (isMe) => ({
        fontSize: '10px',
        color: isMe ? 'rgba(255,255,255,0.7)' : '#999',
        marginTop: '4px',
        textAlign: 'right'
    });

    return (
        <div style={containerStyle}>
            <div style={headerStyle}>
                <button onClick={() => navigate('/chats')}>← Back</button>
                <h3 style={{ margin: 0 }}>Chat #{chatId}</h3>
                <div style={{ width: '40px' }}></div>
            </div>

            <div style={chatBoxStyle}>
                {messages.map((msg, index) => {
                    const isMe = String(msg.senderId) === String(myUserId);
                    return (
                        <div key={index} style={messageWrapperStyle(isMe)}>
                            <div style={messageBubbleStyle(isMe)}>
                                <div>{msg.content}</div>
                                <div style={timeStyle(isMe)}>
                                    {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                                </div>
                            </div>
                        </div>
                    );
                })}
                <div ref={messagesEndRef} />
            </div>

            <div style={{ padding: '15px', borderTop: '1px solid #eee', display: 'flex', gap: '10px' }}>
                <input
                    style={{ flex: 1, padding: '10px 15px', borderRadius: '20px', border: '1px solid #ddd', outline: 'none' }}
                    type="text"
                    placeholder="Aa"
                    value={messageInput}
                    onChange={(e) => setMessageInput(e.target.value)}
                    onKeyPress={(e) => e.key === 'Enter' && sendMessage()}
                />
                <button
                    style={{ padding: '8px 20px', borderRadius: '20px', border: 'none', backgroundColor: '#0084ff', color: '#fff', fontWeight: 'bold', cursor: 'pointer' }}
                    onClick={sendMessage}
                >
                    Send
                </button>
            </div>
        </div>
    );
};

export default ChatRoom;
