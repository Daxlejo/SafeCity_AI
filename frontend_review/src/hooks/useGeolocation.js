import { useState, useCallback, useRef, useEffect } from 'react';

/**
 * Custom Hook: useGeolocation
 *
 * Encapsula toda la complejidad de la API de Geolocalización del navegador
 * en una interfaz limpia y reactiva.
 *
 * Estados posibles:
 * - idle:    No se ha solicitado ubicación aún
 * - loading: Esperando respuesta del navegador
 * - success: Ubicación obtenida correctamente
 * - error:   Falló (permiso denegado, timeout, no disponible)
 *
 * Características:
 * - Caché en localStorage (última ubicación válida, expira en 10 min)
 * - Detección automática de dispositivo (mobile vs desktop)
 * - Reintento configurable
 * - Mensajes de error legibles (no técnicos)
 */

// ═══ CONSTANTES ═══
const CACHE_KEY = 'safecity_last_location';
const CACHE_EXPIRY_MS = 10 * 60 * 1000; // 10 minutos

// Mensajes de error amigables para el usuario (no técnicos)
const ERROR_MESSAGES = {
  permission_denied: 'Permiso de ubicación denegado. Selecciona tu ubicación en el mapa.',
  position_unavailable: 'GPS no disponible en este dispositivo. Selecciona tu ubicación en el mapa.',
  timeout: 'Tiempo agotado obteniendo ubicación. Selecciona tu ubicación en el mapa.',
  not_supported: 'Tu navegador no soporta geolocalización. Selecciona tu ubicación en el mapa.',
};

/**
 * Detecta si el dispositivo es móvil.
 * Móviles generalmente tienen GPS real → enableHighAccuracy tiene sentido.
 * Desktop generalmente usa WiFi/IP → enableHighAccuracy causa timeouts.
 */
const isMobile = () => /Android|iPhone|iPad|iPod|webOS|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);

/**
 * @param {Object} options - Opciones de configuración
 * @param {number} options.timeout - Timeout en ms (default: 10000)
 * @param {number} options.maxRetries - Reintentos (default: 1)
 * @returns {{ location, status, errorMessage, errorType, requestLocation, clearError }}
 */
export default function useGeolocation({ timeout = 10000, maxRetries = 1 } = {}) {
  // ═══ ESTADO PRINCIPAL ═══
  const [location, setLocation] = useState(null);      // { lat, lng }
  const [status, setStatus] = useState('idle');          // idle | loading | success | error
  const [errorType, setErrorType] = useState(null);      // permission_denied | timeout | etc.
  const [errorMessage, setErrorMessage] = useState('');   // Mensaje legible

  // Ref para controlar reintentos sin re-renders
  const retryCount = useRef(0);

  // ═══ CACHÉ: Cargar última ubicación al montar ═══
  useEffect(() => {
    try {
      const cached = localStorage.getItem(CACHE_KEY);
      if (cached) {
        const { lat, lng, timestamp } = JSON.parse(cached);
        const age = Date.now() - timestamp;
        if (age < CACHE_EXPIRY_MS && lat && lng) {
          setLocation({ lat, lng });
          setStatus('success');
        } else {
          localStorage.removeItem(CACHE_KEY);
        }
      }
    } catch {
      localStorage.removeItem(CACHE_KEY);
    }
  }, []);

  // ═══ GUARDAR EN CACHÉ ═══
  const saveToCache = useCallback((lat, lng) => {
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify({ lat, lng, timestamp: Date.now() }));
    } catch {
      // localStorage lleno o no disponible, no pasa nada
    }
  }, []);

  // ═══ LIMPIAR ERROR (para cerrar toasts) ═══
  const clearError = useCallback(() => {
    setErrorType(null);
    setErrorMessage('');
    if (status === 'error') setStatus('idle');
  }, [status]);

  // ═══ SOLICITAR UBICACIÓN ═══
  const requestLocation = useCallback(() => {
    // Verificar soporte del navegador
    if (!navigator.geolocation) {
      setStatus('error');
      setErrorType('not_supported');
      setErrorMessage(ERROR_MESSAGES.not_supported);
      return;
    }

    setStatus('loading');
    setErrorType(null);
    setErrorMessage('');
    retryCount.current = 0;

    const attemptGeolocation = () => {
      const mobile = isMobile();

      navigator.geolocation.getCurrentPosition(
        // ═══ ÉXITO ═══
        (position) => {
          const { latitude: lat, longitude: lng } = position.coords;
          setLocation({ lat, lng });
          setStatus('success');
          saveToCache(lat, lng);
          retryCount.current = 0;
        },

        // ═══ ERROR ═══
        (error) => {
          // Si podemos reintentar → intentar de nuevo
          if (retryCount.current < maxRetries) {
            retryCount.current++;
            setTimeout(attemptGeolocation, 1000);
            return;
          }

          // Mapear código de error a etiqueta legible
          let type;
          switch (error.code) {
            case error.PERMISSION_DENIED:
              type = 'permission_denied';
              break;
            case error.POSITION_UNAVAILABLE:
              type = 'position_unavailable';
              break;
            case error.TIMEOUT:
              type = 'timeout';
              break;
            default:
              type = 'timeout';
          }

          setStatus('error');
          setErrorType(type);
          setErrorMessage(ERROR_MESSAGES[type]);
          retryCount.current = 0;
        },

        // ═══ OPCIONES ═══
        {
          enableHighAccuracy: mobile,  // Solo GPS real en móvil
          timeout: timeout,
          maximumAge: CACHE_EXPIRY_MS, // Reusar posiciones cachedas hasta 10 min
        }
      );
    };

    attemptGeolocation();
  }, [timeout, maxRetries, saveToCache]);

  return {
    location,       // { lat, lng } o null
    status,         // 'idle' | 'loading' | 'success' | 'error'
    errorType,      // 'permission_denied' | 'timeout' | etc.
    errorMessage,   // Mensaje legible para el usuario
    requestLocation, // Función para pedir ubicación
    clearError,     // Función para limpiar el error
  };
}
