/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// A dependency free client for the POST /tickets endpoint of the orchestrator.
//
// Everything that comes back from the server, in particular the summary and the reply written by the model, is
// untrusted. It is only ever written to the page with textContent, never with innerHTML.

const SAMPLES = {
    billing: {
        subject: 'Charged twice for my Pro subscription',
        body: 'Hi, I was billed twice on 3 October for the Pro plan, invoices INV-20931 and INV-20932 are identical. '
            + 'Please refund the duplicate charge.'
    },
    technical: {
        subject: 'Mobile app crashes on login',
        body: 'Since this morning the Android app closes immediately after I enter my password. '
            + 'Version 4.2.1 on a Pixel 8, reinstalling did not help.'
    },
    general: {
        subject: 'Partnership enquiry',
        body: 'We run a consultancy and would like to resell your product to our clients. '
            + 'Who should we talk to about a partnership?'
    }
};

const $ = (selector, root = document) => root.querySelector(selector);

const form = $('#ticket-form');
const subject = $('#subject');
const body = $('#body');
const counter = $('#body-counter');
const submit = $('#submit');
const newTicket = $('#new-ticket');
const flow = $('#flow');
const connectors = $('#connectors');
const result = $('#result');
const elapsed = $('#elapsed');
const historyList = $('#history');
const historyEmpty = $('#history-empty');
const clearHistory = $('#clear-history');
const llm = $('#llm');

const resultHint = $('.placeholder', result).textContent;

const submitted = [];
let timer;

// Form

function updateCounter() {
    counter.textContent = `${body.value.length} / ${body.maxLength}`;
}

document.querySelectorAll('[data-sample]').forEach(chip => chip.addEventListener('click', () => {
    const sample = SAMPLES[chip.dataset.sample];
    subject.value = sample.subject;
    body.value = sample.body;
    subject.classList.remove('invalid');
    body.classList.remove('invalid');
    updateCounter();
    body.focus();
}));

body.addEventListener('input', updateCounter);
[subject, body].forEach(field => field.addEventListener('input', () => field.classList.remove('invalid')));

// Start over with an empty form and an idle flow diagram. The tickets of the session stay in the history.
newTicket.addEventListener('click', () => {
    form.reset();
    subject.classList.remove('invalid');
    body.classList.remove('invalid');
    updateCounter();
    resetFlow();
    elapsed.textContent = '';
    result.classList.remove('appear');
    result.replaceChildren(placeholder(resultHint));
    subject.focus();
});

form.addEventListener('keydown', event => {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
        form.requestSubmit();
    }
});

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (submit.disabled) {
        return;
    }

    const ticket = { subject: subject.value.trim(), body: body.value.trim() };
    const missing = [[subject, ticket.subject], [body, ticket.body]].filter(([, value]) => !value);
    missing.forEach(([field]) => field.classList.add('invalid'));
    if (missing.length) {
        missing[0][0].focus();
        return;
    }

    setBusy(true);
    showFlowWorking();
    const started = performance.now();

    try {
        const response = await fetch('tickets', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify(ticket)
        });
        const payload = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new TicketError(response, payload);
        }

        const entry = { ticket, result: payload, seconds: (performance.now() - started) / 1000, at: new Date() };
        submitted.unshift(entry);
        renderHistory();
        showResult(entry);
    } catch (error) {
        showError(error);
    } finally {
        setBusy(false);
    }
});

class TicketError extends Error {
    constructor(response, payload) {
        let message = payload.error || `The orchestrator answered with HTTP ${response.status}`;
        const retryAfter = response.headers.get('Retry-After');
        if (response.status === 503 && retryAfter) {
            message += ` (retry in ${retryAfter}s)`;
        }
        super(message);
        this.status = response.status;
    }
}

function setBusy(busy) {
    submit.disabled = busy;
    submit.classList.toggle('busy', busy);
    submit.setAttribute('aria-busy', String(busy));
    // The response of a ticket in flight would overwrite a form that was reset in the meantime
    newTicket.disabled = busy;

    clearInterval(timer);
    if (busy) {
        const started = performance.now();
        const tick = () => elapsed.textContent = `${((performance.now() - started) / 1000).toFixed(1)}s`;
        tick();
        timer = setInterval(tick, 100);
    }
}

// Flow diagram. The endpoint is synchronous, so the page cannot tell when the triage ends and the specialist agent
// starts: while waiting, the whole pipeline after the ticket is shown as working, and the path that was actually
// taken is highlighted once the response arrives.

function resetFlow() {
    flow.querySelectorAll('.node, .edge, .connector').forEach(element =>
        element.classList.remove('active', 'working', 'done', 'dim', 'failed'));
}

function mark(selector, ...classes) {
    flow.querySelectorAll(selector).forEach(element => element.classList.add(...classes));
}

function showFlowWorking() {
    resetFlow();
    mark('[data-node="ticket"]', 'done');
    mark('[data-edge="triage"]', 'active');
    mark('[data-node="triage"]', 'active', 'working');
    result.classList.remove('appear');
    result.replaceChildren(placeholder('Triaging the ticket and waiting for the specialist agent…'));
}

function showFlowPath(category) {
    resetFlow();
    mark('[data-node="ticket"], [data-edge], [data-node="triage"]', 'done');
    mark('.branches .node, .connector', 'dim');
    const name = CSS.escape(category || '');
    flow.querySelectorAll(`.branches [data-node="${name}"], .connector[data-branch="${name}"]`).forEach(target => {
        target.classList.remove('dim');
        target.classList.add('done');
    });
}

// One connector runs from the triage agent to each outcome, so that the highlighted one points at the branch that was
// taken. Where they end depends on the height of the nodes, so they are drawn from the measured layout.

function layoutConnectors() {
    const box = connectors.getBoundingClientRect();
    if (!box.width) {
        return; // Hidden in the stacked layout of narrow screens
    }
    const centre = node => {
        const rect = node.getBoundingClientRect();
        return rect.top + rect.height / 2 - box.top;
    };
    const from = centre($('[data-node="triage"]', flow));
    const bend = box.width / 2;
    const tip = box.width;
    connectors.querySelectorAll('.connector').forEach(connector => {
        const to = centre($(`.branches [data-node="${connector.dataset.branch}"]`, flow));
        $('path', connector).setAttribute('d', `M0 ${from}C${bend} ${from} ${bend} ${to} ${tip - 5} ${to}`);
        $('polygon', connector).setAttribute('points', `${tip - 6},${to - 5} ${tip},${to} ${tip - 6},${to + 5}`);
    });
}

new ResizeObserver(layoutConnectors).observe(flow);

// Result

function placeholder(text) {
    const p = document.createElement('p');
    p.className = 'placeholder';
    p.textContent = text;
    return p;
}

function showResult(entry) {
    const data = entry.result;
    showFlowPath(data.category);
    elapsed.textContent = `${entry.seconds.toFixed(1)}s`;

    const view = $('#result-template').content.cloneNode(true);
    setBadge($('.category', view), data.category);
    setBadge($('.priority', view), data.priority, 'priority');
    setBadge($('.status', view), data.status);
    $('.summary', view).textContent = data.summary || '';
    $('.ticket-id', view).textContent = data.ticketId || '—';
    $('.task-id', view).textContent = data.taskId || '—';

    if (data.reply) {
        $('.handled-by', view).textContent = `Draft by ${data.handledBy || 'specialist agent'}`;
        $('.reply', view).textContent = data.reply;
        $('.copy', view).addEventListener('click', copyReply(data.reply));
    } else {
        $('.reply-wrap', view).replaceWith(placeholder(
            'No specialist agent handles this category, so no reply was drafted. A human picks the ticket up.'));
    }

    result.replaceChildren(view);
    void result.offsetWidth; // Restart the entry animation
    result.classList.add('appear');
}

function setBadge(element, value, suffix) {
    if (!value) {
        element.remove();
        return;
    }
    element.textContent = suffix ? `${value} ${suffix}` : value;
    element.classList.add(value);
}

function copyReply(text) {
    return async event => {
        const button = event.currentTarget;
        try {
            await navigator.clipboard.writeText(text);
            button.textContent = 'Copied';
        } catch {
            button.textContent = 'Copy failed';
        }
        setTimeout(() => button.textContent = 'Copy reply', 1500);
    };
}

function showError(error) {
    resetFlow();
    // HTTP 400 means the ticket itself was rejected before any model was called
    mark(error.status === 400 ? '[data-node="ticket"]' : '[data-node="triage"]', 'failed');

    const box = document.createElement('div');
    box.className = 'error';
    box.setAttribute('role', 'alert');
    box.textContent = error instanceof TicketError
        ? error.message
        : 'The orchestrator could not be reached. Is it running on this host?';
    result.replaceChildren(box);
}

// History

function renderHistory() {
    historyList.replaceChildren(...submitted.map(entry => {
        const button = document.createElement('button');
        button.type = 'button';

        const badges = document.createElement('span');
        badges.className = 'badges';
        for (const value of [entry.result.category, entry.result.status]) {
            const badge = document.createElement('span');
            badge.className = 'badge';
            setBadge(badge, value);
            badges.append(badge);
        }

        const title = document.createElement('span');
        title.className = 'subject';
        title.textContent = entry.ticket.subject;

        const when = document.createElement('span');
        when.className = 'when';
        when.textContent = `${entry.at.toLocaleTimeString()} · ${entry.seconds.toFixed(1)}s`;

        button.append(badges, title, when);
        button.addEventListener('click', () => {
            subject.value = entry.ticket.subject;
            body.value = entry.ticket.body;
            updateCounter();
            showResult(entry);
            result.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        });

        const item = document.createElement('li');
        item.append(button);
        return item;
    }));
    historyEmpty.hidden = submitted.length > 0;
    clearHistory.hidden = submitted.length === 0;
}

clearHistory.addEventListener('click', () => {
    submitted.length = 0;
    renderHistory();
});

// LLM indicator. The orchestrator only knows its own configuration: the specialist agents are separate applications
// that may be configured with a different provider.

async function showLlm() {
    try {
        const response = await fetch('llm', { headers: { 'Accept': 'application/json' } });
        if (!response.ok) {
            return;
        }
        const info = await response.json();
        $('.llm-provider', llm).textContent = info.openAi ? 'OpenAI' : `${info.provider} · OpenAI compatible`;
        $('.llm-model', llm).textContent = info.model || '';
        llm.classList.toggle('compatible', !info.openAi);
        llm.title = info.openAi
            ? 'The triage agent calls the OpenAI API'
            : `The triage agent calls an OpenAI compatible API at ${info.provider}, not OpenAI itself`;
        llm.hidden = false;
    } catch {
        // The indicator is optional, the page works without it
    }
}

showLlm();
updateCounter();
