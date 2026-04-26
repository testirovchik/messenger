import React, { useState, useEffect, useRef } from 'react';

const ChatRoom = () => {
    const [messages, setMessages] = useState([]);
    const [messageInput, setMessageInput] = useState('');
    const [myUserId, setMyUserId] = useState('');
    const [receiverId, setReceiverId] = useState('');
    const [ws, setWs] = useState(null);
    const messagesEndRef = useRef(null);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    const connect = () => {
        if (!myUserId) {
            alert("Please enter My User ID first");
            return;
        }

        const socket = new WebSocket("ws://localhost:8080/ws-chat");

        socket.onopen = () => {
            console.log("Connected to WebSocket");
            socket.send(JSON.stringify({ type: "REGISTER", userId: myUserId }));
        };

        socket.onmessage = (event) => {
            const message = JSON.parse(event.data);
            setMessages((prev) => [...prev, message]);
        };

        socket.onclose = () => {
            console.log("WebSocket connection closed");
        };

        socket.onerror = (error) => {
            console.error("WebSocket error:", error);
        };

        setWs(socket);
    };

    const sendMessage = () => {
        if (ws && ws.readyState === WebSocket.OPEN && messageInput.trim()) {
            const messagePayload = {
                type: "MESSAGE",
                receiverId: receiverId,
                content: messageInput,
                senderId: myUserId // Including senderId for UI display
            };

            ws.send(JSON.stringify(messagePayload));

            // Add to local state
            setMessages((prev) => [...prev, messagePayload]);
            setMessageInput('');
        }
    };

    const containerStyle = {
        maxWidth: '600px',
        margin: '20px auto',
        padding: '20px',
        border: '1px solid #ccc',
        borderRadius: '8px',
        fontFamily: 'Arial, sans-serif'
    };

    const chatBoxStyle = {
        height: '400px',
        overflowY: 'scroll',
        border: '1px solid #eee',
        padding: '10px',
        marginBottom: '20px',
        backgroundColor: '#f9f9f9',
        display: 'flex',
        flexDirection: 'column'
    };

    const messageStyle = (isMe) => ({
        alignSelf: isMe ? 'flex-end' : 'flex-start',
        backgroundColor: isMe ? '#0084ff' : '#e4e6eb',
        color: isMe ? 'white' : 'black',
        padding: '8px 12px',
        borderRadius: '15px',
        marginBottom: '8px',
        maxWidth: '70%',
        wordBreak: 'break-word'
    });

    const inputGroupStyle = {
        display: 'flex',
        gap: '10px',
        marginBottom: '10px'
    };

    return (
        <div style={containerStyle}>
            <h2>Chat Room</h2>

            <div style={inputGroupStyle}>
                <input
                    type="text"
                    placeholder="My User ID"
                    value={myUserId}
                    onChange={(e) => setMyUserId(e.target.value)}
                    disabled={ws?.readyState === WebSocket.OPEN}
                />
                <input
                    type="text"
                    placeholder="Receiver User ID"
                    value={receiverId}
                    onChange={(e) => setReceiverId(e.target.value)}
                />
                <button onClick={connect} disabled={ws?.readyState === WebSocket.OPEN}>
                    {ws?.readyState === WebSocket.OPEN ? 'Connected' : 'Connect'}
                </button>
            </div>

            <div style={chatBoxStyle}>
                {messages.map((msg, index) => (
                    <div key={index} style={messageStyle(String(msg.senderId) === String(myUserId))}>
                        {msg.content}
                    </div>
                ))}
                <div ref={messagesEndRef} />
            </div>

            <div style={inputGroupStyle}>
                <input
                    style={{ flex: 1 }}
                    type="text"
                    placeholder="Type a message..."
                    value={messageInput}
                    onChange={(e) => setMessageInput(e.target.value)}
                    onKeyPress={(e) => e.key === 'Enter' && sendMessage()}
                />
                <button onClick={sendMessage}>Send</button>
            </div>
        </div>
    );
};

export default ChatRoom;
