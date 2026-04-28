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
    const wsRef = useRef(null);
    const fileInputRef = useRef(null); // Ref for hidden file input

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
        wsRef.current = socket;

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

        socket.onclose = (e) => {
            console.log("WebSocket closed", e.code, e.reason);
        };

        socket.onerror = (err) => {
            console.error("WebSocket error", err);
        };

        setWs(socket);

        return () => {
            if (wsRef.current) {
                wsRef.current.close();
            }
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
                createdAt: new Date().toISOString(),
                type: "TEXT" // Explicitly set type for optimistic update
            }]);
            setMessageInput('');
        } else if (ws && ws.readyState !== WebSocket.OPEN) {
            console.warn("WebSocket is not open. State:", ws.readyState);
            alert("Connection lost. Please refresh the page.");
        }
    };

    const handleImageUploadClick = () => {
        fileInputRef.current.click(); // Trigger the hidden file input
    };

    const handleFileChange = async (event) => {
        const file = event.target.files[0];
        if (!file) return;

        if (!myUserId) {
            alert("User ID not available. Please log in again.");
            return;
        }

        const token = localStorage.getItem('chat_token');
        if (!token) {
            navigate('/login');
            return;
        }

        const formData = new FormData();
        formData.append('chatId', chatId);
        formData.append('file', file);

        // Optimistic update for the sender
        setMessages((prev) => [...prev, {
            content: URL.createObjectURL(file), // Use object URL for immediate display
            senderId: myUserId,
            createdAt: new Date().toISOString(),
            type: "IMAGE_PENDING" // Temporary type for local display
        }]);

        try {
            const response = await fetch('http://localhost:8081/api/messages/upload', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`
                    // 'Content-Type': 'multipart/form-data' is NOT needed with FormData, browser sets it
                },
                body: formData,
            });

            if (response.ok) {
                const uploadedMessage = await response.json();
                // Replace the pending message with the actual uploaded message (with presigned URL)
                setMessages((prev) => prev.map(msg =>
                    msg.type === "IMAGE_PENDING" && msg.content === URL.createObjectURL(file)
                        ? uploadedMessage
                        : msg
                ));
                // Revoke the object URL to free up memory
                URL.revokeObjectURL(file);
            } else {
                const errorText = await response.text();
                console.error('Image upload failed:', response.status, errorText);
                alert('Failed to upload image: ' + errorText);
                // Remove pending message if upload fails
                setMessages((prev) => prev.filter(msg => !(msg.type === "IMAGE_PENDING" && msg.content === URL.createObjectURL(file))));
            }
        } catch (err) {
            console.error('Network error during image upload:', err);
            alert('Network error during image upload.');
            // Remove pending message if network error
            setMessages((prev) => prev.filter(msg => !(msg.type === "IMAGE_PENDING" && msg.content === URL.createObjectURL(file))));
        } finally {
            // Clear the file input value so the same file can be selected again
            event.target.value = null;
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
                                {msg.type === "IMAGE" || msg.type === "IMAGE_PENDING" ? (
                                    <img src={msg.content} alt="Uploaded" className="chat-image" />
                                ) : (
                                    <div className="message-content">{msg.content}</div>
                                )}
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
                    type="file"
                    ref={fileInputRef}
                    style={{ display: 'none' }}
                    accept="image/*"
                    onChange={handleFileChange}
                />
                <button className="attach-button" onClick={handleImageUploadClick}>
                    📎
                </button>
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
