// -------- Password show/hide toggle --------
// Generalized to support more than one password field per page (e.g. the
// register page has "password" and "confirm password"). Any button with
// class="toggle" and a data-target pointing to an input id will work.
// Falls back to the old #togglePassword / #password pair for backward
// compatibility with the original login markup.
(function initPasswordToggles() {
    var toggles = document.querySelectorAll('.toggle[data-target]');

    toggles.forEach(function (btn) {
        var input = document.getElementById(btn.dataset.target);
        if (!input) return;

        btn.addEventListener('click', function () {
            var showing = input.type === 'text';
            input.type = showing ? 'password' : 'text';
            btn.textContent = showing ? 'Show' : 'Hide';
        });
    });

    // Legacy single-toggle support (original login.html markup)
    var legacyToggle = document.getElementById('togglePassword');
    if (legacyToggle && !legacyToggle.dataset.target) {
        var legacyInput = document.getElementById('password');
        legacyToggle.addEventListener('click', function () {
            if (legacyInput.type === 'password') {
                legacyInput.type = 'text';
                legacyToggle.textContent = 'Hide';
            } else {
                legacyInput.type = 'password';
                legacyToggle.textContent = 'Show';
            }
        });
    }
})();

// -------- Live clock on the welcome / dashboard pages --------
(function initClock() {
    var clock = document.getElementById('clock');
    if (!clock) return;

    function tick() {
        var now = new Date();
        clock.textContent = now.toLocaleTimeString();
    }

    tick();
    setInterval(tick, 1000);
})();

// -------- Notification bell dropdown --------
// Toggles the panel open/closed and closes it on an outside click or Escape.
// No-ops entirely if the page has no #notifBell (e.g. auth pages).
(function initNotifBell() {
    var bell = document.getElementById('notifBell');
    var panel = document.getElementById('notifPanel');
    if (!bell || !panel) return;

    function close() {
        panel.classList.remove('open');
        bell.setAttribute('aria-expanded', 'false');
    }

    bell.addEventListener('click', function (e) {
        e.stopPropagation();
        var isOpen = panel.classList.toggle('open');
        bell.setAttribute('aria-expanded', String(isOpen));
    });

    document.addEventListener('click', function (e) {
        if (!panel.contains(e.target) && e.target !== bell) close();
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') close();
    });
})();

// -------- OTP box auto-advance (two-factor.html) --------
// Purely a front-end convenience: typing a digit jumps to the next box,
// backspace on an empty box jumps back. The actual code still submits as
// one value via a hidden input built on submit.
(function initOtpInputs() {
    var boxes = document.querySelectorAll('.otp-box');
    if (!boxes.length) return;

    var form = document.getElementById('otpForm');
    var hidden = document.getElementById('otpValue');

    function syncHidden() {
        if (!hidden) return;
        var value = '';
        boxes.forEach(function (b) { value += b.value; });
        hidden.value = value;
    }

    boxes.forEach(function (box, i) {
        box.addEventListener('input', function () {
            box.value = box.value.replace(/[^0-9]/g, '').slice(0, 1);
            if (box.value && boxes[i + 1]) boxes[i + 1].focus();
            syncHidden();
        });

        box.addEventListener('keydown', function (e) {
            if (e.key === 'Backspace' && !box.value && boxes[i - 1]) {
                boxes[i - 1].focus();
            }
        });

        // Allow pasting a full code into the first box
        box.addEventListener('paste', function (e) {
            var pasted = (e.clipboardData || window.clipboardData).getData('text').replace(/[^0-9]/g, '');
            if (!pasted) return;
            e.preventDefault();
            pasted.split('').slice(0, boxes.length).forEach(function (digit, idx) {
                if (boxes[idx]) boxes[idx].value = digit;
            });
            var next = boxes[Math.min(pasted.length, boxes.length - 1)];
            if (next) next.focus();
            syncHidden();
        });
    });

    if (form) {
        form.addEventListener('submit', syncHidden);
    }
})();
