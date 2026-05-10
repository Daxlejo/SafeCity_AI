import axios from 'axios';

// ═══════════════════════════════════════════
// CONFIGURACIÓN BASE
// ═══════════════════════════════════════════

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'https://safecity-ai-backend.onrender.com';

const api = axios.create({
  baseURL: `${BACKEND_URL}/api/v1`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

// ═══════════════════════════════════════════
// INTERCEPTOR: agrega JWT automáticamente
// ═══════════════════════════════════════════

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('safecity_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('safecity_token');
      localStorage.removeItem('safecity_user');
      window.location.reload();
    }
    return Promise.reject(error);
  }
);

// ═══════════════════════════════════════════
// AUTH
// ═══════════════════════════════════════════

export const authAPI = {
  login: (credentials) => api.post('/auth/login', credentials),
  register: (data) => api.post('/auth/register', data),
  forgotPassword: (email) => api.post('/auth/forgot-password', { email }),
  resetPassword: (token, newPassword) => api.post('/auth/reset-password', { token, newPassword }),
};

// ═══════════════════════════════════════════
// USERS
// ═══════════════════════════════════════════

export const usersAPI = {
  getMe: () => api.get('/users/me'),
  updateMe: (data) => api.put('/users/me', data),
  changePassword: (currentPassword, newPassword) =>
    api.put('/users/me/password', { currentPassword, newPassword }),
};

// ═══════════════════════════════════════════
// REPORTS
// ═══════════════════════════════════════════

export const reportsAPI = {
  getAll: (page = 0, size = 50) =>
    api.get(`/reports?page=${page}&size=${size}&sort=reportDate&direction=DESC`),
  getById: (id) => api.get(`/reports/${id}`),
  create: (data) => api.post('/reports', data),
  update: (id, data) => api.put(`/reports/${id}`, data),
  delete: (id) => api.delete(`/reports/${id}`),
  // 🔗 Agente 1 — Endpoint de cuota de reportes por hora
  getQuota: () => api.get('/reports/quota'),
};

// ═══════════════════════════════════════════
// ZONES
// ═══════════════════════════════════════════

export const zonesAPI = {
  getAll: () => api.get('/zones'),
  getById: (id) => api.get(`/zones/${id}`),
};

// ═══════════════════════════════════════════
// STATS
// ═══════════════════════════════════════════

export const statsAPI = {
  getSummary: () => api.get('/stats/summary'),
  getByType: () => api.get('/stats/by-type'),
  getByZone: () => api.get('/stats/by-zone'),
  getTimeline: (limit = 10) => api.get(`/stats/timeline?limit=${limit}`),
  getHeatmap: () => api.get('/stats/heatmap'),
  getDangerousZones: (days = 7, limit = 10) =>
    api.get(`/stats/dangerous-zones?days=${days}&limit=${limit}`),
  getDangerousZoneWeek: () => api.get('/stats/dangerous-zone-week'),
};

// ═══════════════════════════════════════════
// NOTIFICATIONS
// ═══════════════════════════════════════════

export const notificationsAPI = {
  getAll: () => api.get('/notifications'),
  getUnread: () => api.get('/notifications/unread'),
  getCount: () => api.get('/notifications/count'),
  markRead: (id) => api.put(`/notifications/${id}/read`),
};

// ═══════════════════════════════════════════
// ADMIN
// ═══════════════════════════════════════════

export const adminAPI = {
  getUsers: (page = 0, size = 20) => api.get(`/admin/users?page=${page}&size=${size}`),
  getUserById: (id) => api.get(`/admin/users/${id}`),
  changeRole: (id, role) => api.put(`/admin/users/${id}/role?role=${role}`),
  toggleBan: (id) => api.put(`/admin/users/${id}/ban`),
  deleteUser: (id) => api.delete(`/admin/users/${id}`),
  updateReportStatus: (id, status) => api.put(`/admin/reports/${id}/status?status=${status}`),
};

// ═══════════════════════════════════════════
// IA
// ═══════════════════════════════════════════

export const iaAPI = {
  classify: (reportId) => api.post(`/ia/classify/${reportId}`),
};

// ═══════════════════════════════════════════
// FILE UPLOAD
// ═══════════════════════════════════════════

export const uploadAPI = {
  uploadPhoto: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post('/uploads', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  getPhotoUrl: (filename) => `${BACKEND_URL}/api/v1/uploads/${filename}`,
};

// ═══════════════════════════════════════════
// OSINT
// ═══════════════════════════════════════════

export const osintAPI = {
  /** Obtiene la configuración actual del módulo OSINT (solo ADMIN) */
  getConfig: () => api.get('/osint/config'),

  /** Actualiza la configuración completa de OSINT (solo ADMIN) */
  updateConfig: (configData) => api.put('/osint/config', configData),

  /** Activa o desactiva el scheduler OSINT rápidamente (solo ADMIN) */
  toggleEnabled: (enabled) => api.put(`/osint/config/toggle?enabled=${enabled}`),

  /** Fuerza un escaneo OSINT inmediato (solo ADMIN) */
  triggerScan: () => api.post('/osint/trigger'),

  /** Obtiene noticias OSINT paginadas (público) */
  getNews: (page = 0, size = 20) => api.get(`/osint/news?page=${page}&size=${size}`),

  /** Obtiene logs de auditoría paginados (solo ADMIN, cuando Agente 1 lo implemente) */
  getAuditLogs: (page = 0, size = 20) => api.get(`/admin/audit-logs?page=${page}&size=${size}`),
};

export { BACKEND_URL };
export default api;

