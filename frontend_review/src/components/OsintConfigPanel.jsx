import { useState, useEffect, useCallback } from 'react';
import { osintAPI } from '../services/api';
import {
  Settings, Power, Clock, Tag, Globe, RefreshCw,
  Plus, X, Zap, Save, AlertTriangle, Check
} from 'lucide-react';

/**
 * Panel de configuración OSINT para administradores.
 * Permite:
 *   - Toggle ON/OFF del scheduler
 *   - Ajustar intervalo de escaneo (minutos)
 *   - Editar keywords de búsqueda (tag input)
 *   - Gestionar URLs/fuentes prioritarias
 *   - Disparar un scan manual
 */
export default function OsintConfigPanel() {
  const [config, setConfig] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [triggering, setTriggering] = useState(false);
  const [triggerResult, setTriggerResult] = useState(null);
  const [newKeyword, setNewKeyword] = useState('');
  const [newUrl, setNewUrl] = useState('');
  const [hasChanges, setHasChanges] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const loadConfig = useCallback(async () => {
    try {
      const res = await osintAPI.getConfig();
      setConfig(res.data);
      setHasChanges(false);
    } catch (err) {
      console.error('Error cargando config OSINT:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadConfig(); }, [loadConfig]);

  const handleToggle = async () => {
    try {
      const res = await osintAPI.toggleEnabled(!config.enabled);
      setConfig(res.data);
    } catch (err) {
      alert('Error al cambiar estado OSINT: ' + (err.response?.data?.message || err.message));
    }
  };

  const handleSave = async () => {
    setSaving(true);
    setSaveSuccess(false);
    try {
      const res = await osintAPI.updateConfig(config);
      setConfig(res.data);
      setHasChanges(false);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch (err) {
      alert('Error al guardar config: ' + (err.response?.data?.message || err.message));
    } finally {
      setSaving(false);
    }
  };

  const handleTriggerScan = async () => {
    setTriggering(true);
    setTriggerResult(null);
    try {
      const res = await osintAPI.triggerScan();
      setTriggerResult(res.data);
    } catch (err) {
      setTriggerResult({ error: err.response?.data?.message || err.message });
    } finally {
      setTriggering(false);
    }
  };

  const updateField = (field, value) => {
    setConfig(prev => ({ ...prev, [field]: value }));
    setHasChanges(true);
  };

  const addKeyword = () => {
    if (!newKeyword.trim() || config.keywords?.includes(newKeyword.trim())) return;
    updateField('keywords', [...(config.keywords || []), newKeyword.trim()]);
    setNewKeyword('');
  };

  const removeKeyword = (kw) => {
    updateField('keywords', (config.keywords || []).filter(k => k !== kw));
  };

  const addUrl = () => {
    if (!newUrl.trim() || config.priorityUrls?.includes(newUrl.trim())) return;
    updateField('priorityUrls', [...(config.priorityUrls || []), newUrl.trim()]);
    setNewUrl('');
  };

  const removeUrl = (url) => {
    updateField('priorityUrls', (config.priorityUrls || []).filter(u => u !== url));
  };

  if (loading) {
    return (
      <div className="osint-config-loading">
        <span className="spinner" />
        <p>Cargando configuración OSINT...</p>
      </div>
    );
  }

  if (!config) {
    return (
      <div className="osint-config-error">
        <AlertTriangle size={24} />
        <p>No se pudo cargar la configuración OSINT</p>
        <button className="btn btn-ghost btn-sm" onClick={loadConfig}>Reintentar</button>
      </div>
    );
  }

  return (
    <div className="osint-config-panel">
      {/* Header con estado global */}
      <div className="osint-config-header">
        <div className="osint-config-title">
          <Settings size={20} />
          <h3>Configuración OSINT</h3>
        </div>
        <div className="osint-toggle-container">
          <span className="osint-toggle-label">
            {config.enabled ? 'Activo' : 'Inactivo'}
          </span>
          <button
            className={`osint-toggle-btn ${config.enabled ? 'active' : ''}`}
            onClick={handleToggle}
            title={config.enabled ? 'Desactivar scheduler' : 'Activar scheduler'}
          >
            <Power size={14} />
            <span className="osint-toggle-dot" />
          </button>
        </div>
      </div>

      {/* Intervalo de escaneo */}
      <div className="osint-config-section">
        <label className="osint-config-label">
          <Clock size={14} />
          Intervalo de escaneo
        </label>
        <div className="osint-interval-control">
          <input
            type="range"
            min="5"
            max="360"
            step="5"
            value={config.intervalMinutes}
            onChange={(e) => updateField('intervalMinutes', parseInt(e.target.value))}
            className="osint-slider"
          />
          <span className="osint-interval-value">{config.intervalMinutes} min</span>
        </div>
      </div>

      {/* Ciudad por defecto */}
      <div className="osint-config-section">
        <label className="osint-config-label">
          <Globe size={14} />
          Ciudad por defecto
        </label>
        <input
          type="text"
          className="form-input"
          value={config.defaultCity || ''}
          onChange={(e) => updateField('defaultCity', e.target.value)}
          placeholder="Ej: Pasto"
        />
      </div>

      {/* Max ítems por ejecución */}
      <div className="osint-config-section">
        <label className="osint-config-label">
          <Zap size={14} />
          Máx. ítems por ejecución
        </label>
        <input
          type="number"
          className="form-input"
          min="1"
          max="50"
          value={config.maxItemsPerExecution}
          onChange={(e) => updateField('maxItemsPerExecution', parseInt(e.target.value) || 10)}
        />
      </div>

      {/* Keywords (Tag Input) */}
      <div className="osint-config-section">
        <label className="osint-config-label">
          <Tag size={14} />
          Keywords de búsqueda
        </label>
        <div className="osint-tags-container">
          {(config.keywords || []).map((kw, i) => (
            <span key={i} className="osint-tag">
              {kw}
              <button className="osint-tag-remove" onClick={() => removeKeyword(kw)}>
                <X size={10} />
              </button>
            </span>
          ))}
        </div>
        <div className="osint-tag-input-row">
          <input
            type="text"
            className="form-input"
            value={newKeyword}
            onChange={(e) => setNewKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addKeyword())}
            placeholder="Agregar keyword..."
          />
          <button className="btn btn-ghost btn-sm" onClick={addKeyword} disabled={!newKeyword.trim()}>
            <Plus size={14} />
          </button>
        </div>
      </div>

      {/* URLs prioritarias */}
      <div className="osint-config-section">
        <label className="osint-config-label">
          <Globe size={14} />
          Fuentes prioritarias
        </label>
        <div className="osint-urls-list">
          {(config.priorityUrls || []).map((url, i) => (
            <div key={i} className="osint-url-item">
              <span className="osint-url-text">{url}</span>
              <button className="osint-tag-remove" onClick={() => removeUrl(url)}>
                <X size={12} />
              </button>
            </div>
          ))}
        </div>
        <div className="osint-tag-input-row">
          <input
            type="text"
            className="form-input"
            value={newUrl}
            onChange={(e) => setNewUrl(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addUrl())}
            placeholder="Nombre de fuente o URL..."
          />
          <button className="btn btn-ghost btn-sm" onClick={addUrl} disabled={!newUrl.trim()}>
            <Plus size={14} />
          </button>
        </div>
      </div>

      {/* Botones de acción */}
      <div className="osint-config-actions">
        <button
          className="btn btn-primary"
          onClick={handleSave}
          disabled={saving || !hasChanges}
        >
          {saving ? <span className="spinner" style={{ width: 14, height: 14 }} /> : saveSuccess ? <Check size={14} /> : <Save size={14} />}
          {saving ? 'Guardando...' : saveSuccess ? '¡Guardado!' : 'Guardar cambios'}
        </button>
        <button
          className="btn btn-ghost"
          onClick={handleTriggerScan}
          disabled={triggering}
        >
          {triggering ? <span className="spinner" style={{ width: 14, height: 14 }} /> : <RefreshCw size={14} />}
          {triggering ? 'Escaneando...' : 'Forzar scan ahora'}
        </button>
      </div>

      {/* Resultado del trigger */}
      {triggerResult && (
        <div className={`osint-trigger-result ${triggerResult.error ? 'error' : 'success'}`}>
          {triggerResult.error ? (
            <p><AlertTriangle size={14} /> Error: {triggerResult.error}</p>
          ) : (
            <>
              <p><Check size={14} /> Scan completado para "{triggerResult.city}"</p>
              <div className="osint-trigger-stats">
                <span>Encontrados: {triggerResult.found}</span>
                <span>Procesados: {triggerResult.processed}</span>
                <span>Reportes: {triggerResult.reportsCreated}</span>
                <span>Noticias: {triggerResult.savedAsNews}</span>
                <span>Duplicados: {triggerResult.duplicatesSkipped}</span>
              </div>
            </>
          )}
        </div>
      )}

      {/* Última actualización */}
      {config.updatedAt && (
        <p className="osint-config-updated">
          Última actualización: {new Date(config.updatedAt).toLocaleString('es-CO')}
        </p>
      )}
    </div>
  );
}
