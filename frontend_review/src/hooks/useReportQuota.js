import { useState, useEffect, useCallback } from 'react';
import { reportsAPI } from '../services/api';

// ═══════════════════════════════════════════
// HOOK: useReportQuota (copia para frontend_review)
// ═══════════════════════════════════════════
// Consume el endpoint GET /api/reports/quota (Agente 1)
// y expone los datos de cuota del usuario actual.
// ═══════════════════════════════════════════

export default function useReportQuota(enabled = true) {
  const [quota, setQuota] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchQuota = useCallback(async () => {
    if (!enabled) return;
    setLoading(true);
    setError(null);
    try {
      const res = await reportsAPI.getQuota();
      setQuota(res.data);
    } catch (err) {
      if (err.response?.status === 404) {
        setQuota({ limit: 5, used: 0, remaining: 5, resetsAt: null });
      } else {
        setError(err.response?.data?.message || 'Error al obtener cuota de reportes');
      }
    } finally {
      setLoading(false);
    }
  }, [enabled]);

  useEffect(() => {
    fetchQuota();
  }, [fetchQuota]);

  return { quota, loading, error, refresh: fetchQuota };
}
