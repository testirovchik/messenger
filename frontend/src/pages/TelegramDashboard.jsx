import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { jwtDecode } from 'jwt-decode'
import api from '../api/axios'
import './TelegramDashboard.css'

function safeDecode(token) {
  try {
    return jwtDecode(token)
  } catch {
    return null
  }
}

function toTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function previewForSidebar(msg) {
  if (!msg) return ''
  if (msg.deleted || msg.content === '__DELETED__') return 'This message was deleted'
  if (msg.type === 'IMAGE') return '📷 Photo'
  return msg.content ?? ''
}

export default function TelegramDashboard() {
  const navigate = useNavigate()

  const [loading, setLoading] = useState(true)
  const [chats, setChats] = useState([])
  const [activeChat, setActiveChat] = useState(null)
  const [messages, setMessages] = useState([])

  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState([])
  const [isSearching, setIsSearching] = useState(false)

  const [messageInput, setMessageInput] = useState('')
  const [isUploading, setIsUploading] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [editingDraft, setEditingDraft] = useState('')

  const messagesListRef = useRef(null)
  const fileInputRef = useRef(null)
  const wsRef = useRef(null)
  const myIdentityRef = useRef({ userId: null, email: null })
  const searchTimerRef = useRef(null)
  const searchSeqRef = useRef(0)

  // FIX: Added activeChatRef to solve the stale closure problem
  const activeChatRef = useRef(null)

  const token = useMemo(() => localStorage.getItem('chat_token') || '', [])
  const decoded = useMemo(() => (token ? safeDecode(token) : null), [token])
  const myUserId = decoded?.sub ?? null
  const myEmail = decoded?.email ?? null

  function ensureAuthed() {
    if (!token || !decoded?.sub) {
      navigate('/login')
      return false
    }
    return true
  }

  function scrollToBottom(behavior = 'auto') {
    const el = messagesListRef.current
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior })
  }

  // FIX: Sync activeChat state to the ref
  useEffect(() => {
    activeChatRef.current = activeChat
  }, [activeChat])

  // Initial boot: auth, fetch chats, connect websocket once.
  useEffect(() => {
    if (!ensureAuthed()) return

    myIdentityRef.current = { userId: decoded.sub, email: myEmail }

    let alive = true
    async function boot() {
      try {
        const res = await api.get('/api/chats/my-chats')
        if (!alive) return
        setChats(Array.isArray(res.data) ? res.data : [])
      } catch (e) {
        console.error('Error fetching chats:', e)
      } finally {
        if (alive) setLoading(false)
      }
    }

    boot()

    const ws = new WebSocket('ws://messenger.local/ws-chat')
    wsRef.current = ws

    ws.onopen = () => {
      ws.send(
        JSON.stringify({
          type: 'REGISTER',
          token,
          email: myEmail,
        })
      )
    }

    ws.onmessage = (event) => {
      let data
      try {
        data = JSON.parse(event.data)
      } catch {
        return
      }

      const chatId = data?.chatId
      const type = data?.type

      // Sidebar preview bump (even if not active chat)
      if (chatId != null && (type === 'MESSAGE' || type === 'IMAGE')) {
        setChats((prev) => {
          const next = prev.map((c) =>
            String(c.id) === String(chatId)
              ? {
                  ...c,
                  lastMessage: type === 'IMAGE' ? '📷 Photo' : data.content,
                  lastMessageAt: data.createdAt ?? new Date().toISOString(),
                }
              : c
          )

          // Move active chat to top-ish behavior
          const idx = next.findIndex((c) => String(c.id) === String(chatId))
          if (idx > 0) {
            const [hit] = next.splice(idx, 1)
            next.unshift(hit)
          }
          return next
        })
      }

      // FIX: Use activeChatRef.current instead of the stale activeChat state
      const currentActiveChat = activeChatRef.current;
      if (!currentActiveChat || String(currentActiveChat.id) !== String(chatId)) return

      if (type === 'MESSAGE' || type === 'IMAGE') {
        setMessages((prev) => {
          // Best-effort reconcile: replace a pending "sending" message from me.
          if (String(data.senderId) === String(myIdentityRef.current.userId)) {
            const i = prev.findIndex(
              (m) =>
                m.clientTempId &&
                m.status === 'sending' &&
                String(m.chatId) === String(chatId) &&
                m.type === type &&
                String(m.senderId) === String(data.senderId) &&
                (type === 'IMAGE' || (m.content ?? '') === (data.content ?? ''))
            )
            if (i !== -1) {
              const copy = prev.slice()
              copy[i] = { ...copy[i], ...data, status: 'sent', clientTempId: null }
              return copy
            }
          }

          return [...prev, { ...data, status: 'sent' }]
        })

        requestAnimationFrame(() => scrollToBottom('smooth'))
        return
      }

      if (type === 'DELETE_MESSAGE') {
        setMessages((prev) =>
          prev.map((m) =>
            String(m.id) === String(data.messageId ?? data.id)
              ? {
                  ...m,
                  deleted: true,
                  content: 'This message was deleted',
                }
              : m
          )
        )
        return
      }

      if (type === 'EDIT_MESSAGE') {
        setMessages((prev) =>
          prev.map((m) =>
            String(m.id) === String(data.messageId ?? data.id)
              ? {
                  ...m,
                  content: data.newContent ?? data.content ?? m.content,
                  edited: true,
                }
              : m
          )
        )
      }
    }

    ws.onerror = (e) => console.error('WebSocket error:', e)

    return () => {
      alive = false
      try {
        ws.close()
      } catch {
        // ignore
      }
      wsRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Load active chat history (Spring Data Page newest->oldest => reverse for display)
  useEffect(() => {
    if (!activeChat) return
    if (!ensureAuthed()) return

    let alive = true
    async function load() {
      try {
        const res = await api.get(`/api/messages/${activeChat.id}?page=0&size=30`)
        const page = res.data
        const content = Array.isArray(page?.content) ? page.content : []
        const ordered = content.slice().reverse()
        if (!alive) return
        setMessages(ordered)
        requestAnimationFrame(() => scrollToBottom('auto'))
      } catch (e) {
        console.error('Error fetching history:', e)
      }
    }
    load()
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeChat?.id])

  async function runSearch(q, seq) {
    try {
      const res = await api.get(`/api/users/search?query=${encodeURIComponent(q)}`)
      if (seq !== searchSeqRef.current) return
      setSearchResults(Array.isArray(res.data) ? res.data : [])
    } catch (e) {
      console.error('Search error:', e)
      if (seq === searchSeqRef.current) setSearchResults([])
    } finally {
      if (seq === searchSeqRef.current) setIsSearching(false)
    }
  }

  async function startPrivateChat(partnerId) {
    try {
      const res = await api.post(`/api/chats/private?partnerId=${encodeURIComponent(partnerId)}`)
      const newChat = res.data

      setSearchQuery('')
      setSearchResults([])

      setChats((prev) => {
        const exists = prev.find((c) => String(c.id) === String(newChat?.id))
        if (exists) return prev
        return [newChat, ...prev]
      })

      setActiveChat(newChat)
    } catch (e) {
      console.error('Create chat error:', e)
      alert('Error creating chat')
    }
  }

  function optimisticPush(msg) {
    setMessages((prev) => [...prev, msg])
    setChats((prev) =>
      prev.map((c) =>
        activeChat && String(c.id) === String(activeChat.id)
          ? { ...c, lastMessage: previewForSidebar(msg) }
          : c
      )
    )
    requestAnimationFrame(() => scrollToBottom('smooth'))
  }

  function sendText() {
    if (!activeChat) return
    const content = messageInput.trim()
    if (!content) return

    const ws = wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      alert('Reconnecting… please try again.')
      return
    }

    const clientTempId = `tmp_${Date.now()}_${Math.random().toString(16).slice(2)}`
    const optimistic = {
      id: null,
      clientTempId,
      type: 'MESSAGE',
      chatId: activeChat.id,
      content,
      senderId: myUserId,
      createdAt: new Date().toISOString(),
      status: 'sending',
    }

    optimisticPush(optimistic)
    setMessageInput('')

    ws.send(
      JSON.stringify({
        type: 'MESSAGE',
        chatId: activeChat.id,
        content,
        senderId: myUserId,
      })
    )
  }

  function onPickAttachment() {
    if (!activeChat) return
    fileInputRef.current?.click()
  }

  async function onFileChange(e) {
    const file = e.target.files?.[0]
    if (!file || !activeChat) return
    setIsUploading(true)

    const clientTempId = `tmpimg_${Date.now()}_${Math.random().toString(16).slice(2)}`
    const localUrl = URL.createObjectURL(file)
    optimisticPush({
      id: null,
      clientTempId,
      type: 'IMAGE',
      chatId: activeChat.id,
      content: localUrl,
      senderId: myUserId,
      createdAt: new Date().toISOString(),
      status: 'sending',
      isLocalObjectUrl: true,
    })

    const formData = new FormData()
    formData.append('chatId', String(activeChat.id))
    formData.append('file', file)

    try {
      await api.post('/api/messages/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      // Server will broadcast IMAGE over WS; reconciliation happens there best-effort.
    } catch (err) {
      console.error('Upload failed:', err)
      setMessages((prev) =>
        prev.map((m) =>
          m.clientTempId === clientTempId ? { ...m, status: 'failed' } : m
        )
      )
      alert('Failed to send image.')
    } finally {
      setIsUploading(false)
      e.target.value = ''
    }
  }

  async function deleteMessage(msg) {
    if (!msg?.id) return
    if (String(msg.senderId) !== String(myUserId)) return

    // Optimistic UI
    setMessages((prev) =>
      prev.map((m) =>
        String(m.id) === String(msg.id)
          ? { ...m, deleted: true, content: 'This message was deleted' }
          : m
      )
    )

    try {
      await api.delete(`/api/messages/${msg.id}`)
    } catch (e) {
      console.error('Delete failed:', e)
      // Best-effort rollback
      setMessages((prev) =>
        prev.map((m) =>
          String(m.id) === String(msg.id) ? { ...m, deleted: false } : m
        )
      )
      alert('Delete failed.')
    }
  }

  function beginEdit(msg) {
    if (!msg?.id) return
    if (String(msg.senderId) !== String(myUserId)) return
    if (msg.deleted) return
    setEditingId(msg.id)
    setEditingDraft(msg.content ?? '')
  }

  async function saveEdit(msgId) {
    const nextText = editingDraft.trim()
    if (!nextText) return

    // Optimistic UI
    setMessages((prev) =>
      prev.map((m) =>
        String(m.id) === String(msgId) ? { ...m, content: nextText, edited: true } : m
      )
    )
    setEditingId(null)
    setEditingDraft('')

    try {
      await api.patch(`/api/messages/${msgId}?newContent=${encodeURIComponent(nextText)}`)
    } catch (e) {
      console.error('Edit failed:', e)
      alert('Edit failed.')
    }
  }

  function cancelEdit() {
    setEditingId(null)
    setEditingDraft('')
  }

  if (loading) {
    return (
      <div className="loading" style={{ padding: 20, textAlign: 'center' }}>
        Connecting…
      </div>
    )
  }

  return (
    <div className="telegram-container">
      <div className="sidebar">
        <div className="sidebar-header">
          <div className="menu-icon" title="Menu">
            ☰
          </div>
          <div className="search-container">
            <input
              type="text"
              className="search-input"
              placeholder="Search users…"
              value={searchQuery}
              onChange={(e) => {
                const next = e.target.value
                setSearchQuery(next)
                if (!next.trim()) {
                  searchSeqRef.current += 1
                  if (searchTimerRef.current) clearTimeout(searchTimerRef.current)
                  setSearchResults([])
                  setIsSearching(false)
                  return
                }

                if (!ensureAuthed()) return
                searchSeqRef.current += 1
                const mySeq = searchSeqRef.current
                if (searchTimerRef.current) clearTimeout(searchTimerRef.current)
                setIsSearching(true)
                searchTimerRef.current = setTimeout(() => {
                  runSearch(next.trim(), mySeq)
                }, 350)
              }}
            />
          </div>
        </div>

        <div className="chat-list">
          {searchQuery.trim() ? (
            <>
              <div className="sidebar-section-title">Global Search</div>
              {isSearching && <div className="sidebar-muted">Searching…</div>}
              {!isSearching && searchResults.length === 0 && (
                <div className="sidebar-muted">No users found</div>
              )}
              {searchResults.map((u) => (
                <div key={u.id} className="chat-item" onClick={() => startPrivateChat(u.id)}>
                  <div className="avatar" style={{ backgroundColor: '#6b9fcb' }}>
                    {(u.username?.[0] || u.email?.[0] || 'U').toUpperCase()}
                  </div>
                  <div className="chat-info">
                    <div className="chat-top-row">
                      <span className="chat-name">{u.username || u.email || `User #${u.id}`}</span>
                    </div>
                    <div className="chat-preview sidebar-action-hint">Click to start chat</div>
                  </div>
                </div>
              ))}
            </>
          ) : (
            chats.map((chat) => (
              <div
                key={chat.id}
                className={`chat-item ${activeChat?.id === chat.id ? 'active' : ''}`}
                onClick={() => setActiveChat(chat)}
              >
                <div className="avatar">
                  {(chat.title?.[0] || chat.partnerUsername?.[0] || 'C').toUpperCase()}
                </div>
                <div className="chat-info">
                  <div className="chat-top-row">
                    <span className="chat-name">{chat.title || chat.partnerUsername || `Chat #${chat.id}`}</span>
                    <span className="chat-time">{toTime(chat.lastMessageAt)}</span>
                  </div>
                  <div className="chat-preview">{chat.lastMessage || ''}</div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      <div className="main-chat-area">
        {!activeChat ? (
          <div className="empty-state">
            <div className="empty-badge">Select a chat to start messaging</div>
          </div>
        ) : (
          <>
            <div className="chat-header">
              <div className="header-avatar">
                {(activeChat.title?.[0] || activeChat.partnerUsername?.[0] || 'C').toUpperCase()}
              </div>
              <div className="header-info">
                <div className="header-name">
                  {activeChat.title || activeChat.partnerUsername || `Chat #${activeChat.id}`}
                </div>
                <div className="header-status">online</div>
              </div>
            </div>

            <div className="messages-list" ref={messagesListRef}>
              {messages.map((msg) => {
                const isMe = String(msg.senderId) === String(myUserId)
                const isDeleted = msg.deleted || msg.content === 'This message was deleted'
                const isEditing = editingId != null && String(editingId) === String(msg.id)
                const stableKey =
                  msg.id ??
                  msg.clientTempId ??
                  `${msg.chatId ?? 'c'}_${msg.senderId ?? 'u'}_${msg.createdAt ?? ''}_${String(msg.content ?? '').slice(0, 32)}`

                return (
                  <div key={stableKey} className={`message-wrapper ${isMe ? 'me' : 'others'}`}>
                    <div className={`message-bubble ${msg.status === 'failed' ? 'failed' : ''}`}>
                      {msg.type === 'IMAGE' ? (
                        <img className="message-image" src={msg.content} alt="attachment" />
                      ) : isEditing ? (
                        <div className="edit-row">
                          <input
                            className="edit-input"
                            value={editingDraft}
                            onChange={(e) => setEditingDraft(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') saveEdit(msg.id)
                              if (e.key === 'Escape') cancelEdit()
                            }}
                            autoFocus
                          />
                          <button className="edit-btn" onClick={() => saveEdit(msg.id)}>
                            Save
                          </button>
                          <button className="edit-btn secondary" onClick={cancelEdit}>
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <span className={isDeleted ? 'message-deleted' : ''}>
                          {isDeleted ? 'This message was deleted' : msg.content}
                        </span>
                      )}

                      <div className="message-meta">
                        {msg.edited && !isDeleted && <span className="message-edited">edited</span>}
                        {msg.status === 'sending' && <span className="message-status">sending…</span>}
                        {msg.status === 'failed' && <span className="message-status error">failed</span>}
                        <span className="message-time">{toTime(msg.createdAt)}</span>
                      </div>

                      {isMe && !isDeleted && msg.id && (
                        <div className="message-actions">
                          <button className="msg-action" onClick={() => beginEdit(msg)}>
                            Edit
                          </button>
                          <button className="msg-action danger" onClick={() => deleteMessage(msg)}>
                            Delete
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>

            <div className="input-area">
              <div className="input-wrapper">
                <input
                  type="file"
                  accept="image/*"
                  style={{ display: 'none' }}
                  ref={fileInputRef}
                  onChange={onFileChange}
                />
                <button
                  type="button"
                  className="attachment-icon-btn"
                  onClick={onPickAttachment}
                  disabled={isUploading}
                  title="Attach image"
                >
                  {isUploading ? '⏳' : '📎'}
                </button>
                <input
                  type="text"
                  className="message-input"
                  placeholder={isUploading ? 'Uploading…' : 'Write a message…'}
                  value={messageInput}
                  onChange={(e) => setMessageInput(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && sendText()}
                  disabled={isUploading}
                />
              </div>
              <button className="send-button-circle" onClick={sendText} disabled={isUploading}>
                <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                  <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"></path>
                </svg>
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}