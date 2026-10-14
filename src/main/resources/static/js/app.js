/*
 * Zamu dashboard: live updates and handover alerts (spec: Live updates; HO-03 in-dashboard alert).
 * One EventSource per tab on GET /app/stream. Server events become htmx triggers on <body>:
 *   "inbox"               -> zamu:inbox          (inbox list refreshes)
 *   "conversation-{id}"   -> zamu:conversation   (open conversation refreshes)
 *   "handover"            -> zamu:inbox, plus a browser notification and a short sound
 * No inline scripts: the CSP is script-src 'self'.
 */
(function () {
  'use strict';
  var body = document.body;
  if (!body || body.dataset.live !== 'on' || !window.EventSource) {
    return;
  }

  var trigger = function (name, detail) {
    if (window.htmx) {
      window.htmx.trigger(body, name, detail || {});
    }
  };

  var source = new EventSource(body.dataset.stream);
  source.addEventListener('inbox', function (e) { trigger('zamu:inbox', { id: e.data }); });
  source.addEventListener('handover', function (e) {
    trigger('zamu:inbox', { id: e.data });
    alertHandover(e.data);
  });
  var openConversation = document.querySelector('[data-conversation-id]');
  if (openConversation) {
    source.addEventListener('conversation-' + openConversation.dataset.conversationId, function () {
      trigger('zamu:conversation');
    });
  }
  // EventSource reconnects by itself after errors; nothing else to do.

  // ---- Alerts --------------------------------------------------------------------------------------
  var audio = null;
  var toggle = document.querySelector('[data-alerts-toggle]');
  var alertsOn = function () {
    return 'Notification' in window && Notification.permission === 'granted';
  };
  if (toggle && 'Notification' in window && !alertsOn() && Notification.permission !== 'denied') {
    toggle.hidden = false;
    toggle.addEventListener('click', function () {
      Notification.requestPermission().then(function () {
        toggle.hidden = alertsOn();
      });
      unlockSound();
    });
  }
  // Browsers only allow sound after the user has interacted with the page.
  document.addEventListener('click', unlockSound, { once: true });

  function unlockSound() {
    if (!audio && (window.AudioContext || window.webkitAudioContext)) {
      audio = new (window.AudioContext || window.webkitAudioContext)();
    }
  }

  function beep() {
    if (!audio) {
      return;
    }
    var osc = audio.createOscillator();
    var gain = audio.createGain();
    osc.type = 'sine';
    osc.frequency.value = 880;
    gain.gain.setValueAtTime(0.0001, audio.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.2, audio.currentTime + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, audio.currentTime + 0.4);
    osc.connect(gain).connect(audio.destination);
    osc.start();
    osc.stop(audio.currentTime + 0.45);
  }

  function alertHandover(conversationId) {
    beep();
    if (!alertsOn()) {
      return;
    }
    // No customer details in the notification: it can show on a locked screen.
    var n = new Notification('Zamu: a customer needs a person', {
      body: 'Open the inbox to accept the chat.',
      tag: 'zamu-handover-' + conversationId
    });
    n.onclick = function () {
      window.focus();
      window.location.href = body.dataset.conversationBase + conversationId;
    };
  }
})();
