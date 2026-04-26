import { Routes, Route, Navigate } from 'react-router-dom'
import Login from './pages/Login'
import Signup from './pages/Signup'
import TelegramDashboard from './pages/TelegramDashboard'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/signup" element={<Signup />} />
      <Route path="/chats" element={<TelegramDashboard />} />
      {/* Keeping old routes just in case, but redirecting to the new dashboard */}
      <Route path="/chat/:chatId" element={<TelegramDashboard />} />
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}
