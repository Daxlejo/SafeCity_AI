import { Client } from '@stomp/stompjs';
import { BACKEND_URL } from './api';

const WS_URL = BACKEND_URL.replace('https://', 'wss://').replace('http://', 'ws://') + '/ws';

/** @type {Client|null} */
let stompClient = null;
let onReportCallback = null;
let onUpdateCallback = null;
let onDeleteCallback = null;

/**
 * Handles document visibility changes (mobile tab backgrounding/foregrounding).
 * Mobile browsers aggressively suspend inactive tabs, silently killing
 * WebSocket connections. This listener forces a reconnection when the
 * user returns to the app, ensuring real-time sync is always restored.
 */
function handleVisibilityChange() {
  if (document.visibilityState === 'visible' && stompClient && !stompClient.connected) {
    console.info('[WebSocket] Tab became visible — forcing reconnect.');
    stompClient.deactivate().then(() => {
      stompClient.activate();
    });
  }
}

/**
 * Establishes a STOMP WebSocket connection with automatic reconnection
 * and mobile lifecycle awareness (via Page Visibility API).
 *
 * @param {Function} onReport  - Callback for new report events.
 * @param {Function} onUpdate  - Callback for report update events.
 * @param {Function} onDelete  - Callback for report deletion events.
 * @returns {Client} The active STOMP client instance.
 */
export function connectWebSocket(onReport, onUpdate, onDelete) {
  onReportCallback = onReport;
  onUpdateCallback = onUpdate;
  onDeleteCallback = onDelete;

  stompClient = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });

  stompClient.onConnect = () => {
    console.info('[WebSocket] Connected to', WS_URL);

    // Subscribe to NEW reports
    stompClient.subscribe('/topic/reports/ALL', (message) => {
      try {
        const report = JSON.parse(message.body);
        if (onReportCallback) onReportCallback(report);
      } catch (err) {
        console.error('[WebSocket] Error parsing new report message:', err);
      }
    });

    // Subscribe to UPDATED reports (e.g. AI changed status to VERIFIED)
    stompClient.subscribe('/topic/reports/updated', (message) => {
      try {
        const report = JSON.parse(message.body);
        if (onUpdateCallback) onUpdateCallback(report);
      } catch (err) {
        console.error('[WebSocket] Error parsing update message:', err);
      }
    });

    // Subscribe to DELETED reports (e.g. AI purged junk report with score 0)
    stompClient.subscribe('/topic/reports/deleted', (message) => {
      try {
        const reportId = Number(message.body);
        if (onDeleteCallback) onDeleteCallback(reportId);
      } catch (err) {
        console.error('[WebSocket] Error parsing delete message:', err);
      }
    });
  };

  stompClient.onStompError = (frame) => {
    console.error('[WebSocket] STOMP error:', frame.headers?.message);
  };

  stompClient.onWebSocketClose = () => {
    console.warn('[WebSocket] Connection closed — will attempt reconnect.');
  };

  // Register the visibility change listener for mobile lifecycle handling
  document.addEventListener('visibilitychange', handleVisibilityChange);

  stompClient.activate();
  return stompClient;
}

/**
 * Gracefully disconnects the STOMP client and removes lifecycle listeners.
 */
export function disconnectWebSocket() {
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
  }
}

/**
 * Returns whether the STOMP client is currently connected.
 * @returns {boolean}
 */
export function isConnected() {
  return stompClient?.connected || false;
}
