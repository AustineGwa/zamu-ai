# Zamu voice channel: proposal

Status: **proposed, for approval**. Not part of the specification until accepted. Written 14 October 2026.

## Summary

Zamu answers phone calls as well as WhatsApp. Calls reach Zamu through the operator's PBX and SIP trunk, one
phone number per business. The assistant talks in English and Swahili, and does what it does on WhatsApp:
answers from the approved knowledge base, books, reschedules and cancels, requests M-Pesa deposits, and hands
the call to staff. Each call is stored as a conversation with channel `voice`, with a summary and the actions
taken, and appears in the same inbox as WhatsApp chats.

**Scope change:** the specification and launch plan list voice as out of scope for version 1. This proposal
places it as **Milestone 9, after version 1 is accepted**, preceded by a two-week spike. The conversation
engine built in Milestone 2 is already channel-neutral (`conversations.channel`), so no rework is expected.

## Decisions taken (14 October 2026)

| Decision | Choice |
| --- | --- |
| When | After WhatsApp version 1 (Milestone 9) |
| How to choose the technology | Two-week spike comparing option A and option B on real Kenyan calls |
| What is stored | A summary and structured notes per call. No recordings and no full transcripts by default |
| PBX and trunk | Operator-run PBX and SIP trunk. **Open: Asterisk or FreeSWITCH, and which trunk provider** |

## The two options for the spike

**A. Speech-to-speech model over SIP.** The PBX sends the call to the AI provider's SIP endpoint. The
provider calls Zamu's webhook; Zamu accepts the call with the tenant's instructions and runs the call over a
control WebSocket: tool calls (find slots, hold slot, request payment, hand over), transfer by SIP REFER to a
staff extension, hang up. No audio passes through Zamu.

**B. Cascaded pipeline.** Asterisk AudioSocket (or FreeSWITCH media streaming) sends call audio to a Zamu
voice gateway. Streaming speech-to-text (a Swahili-capable provider), the same conversation engine and
guardrails as WhatsApp, then text-to-speech back to the caller.

| | A. Speech-to-speech | B. Cascaded |
| --- | --- | --- |
| Turn latency | Under 1 second | About 1 to 2.5 seconds |
| AI-05 (no numbers outside the context) | Prompt only; can't check before the caller hears it | Checked before speaking |
| Provider-agnostic (AI-10) | Tied to one voice provider | Each part swappable |
| Audio through Zamu servers | No | Yes; Zamu runs a media gateway |
| Shares the WhatsApp engine | Tools only | Fully |

**Spike exit criteria** (measured on at least 50 test calls in English, Swahili and mixed speech over the
real trunk): median response latency, booking task success rate, Swahili comprehension errors, interruption
handling, and cost per minute. Pick the option that meets VOI-10 and VOI-11 at the lower cost; if neither
meets VOI-11, voice is not launched.

## Requirements (VOI)

| ID | Requirement | Priority |
| --- | --- | --- |
| VOI-01 | Route each inbound call to its tenant by the dialled number (DID); reject calls to unknown numbers | Must |
| VOI-02 | Open the call with the automated-assistant disclosure, the business name, and that the call is transcribed for notes | Must |
| VOI-03 | Identify the customer by caller ID where available; create the customer on first contact; withheld numbers can still be served but cannot pay by default number | Must |
| VOI-04 | Answer only from approved knowledge base chunks and the tenant profile, with the same guardrails as AI-01 to AI-08 as far as the chosen option allows | Must |
| VOI-05 | Booking, rescheduling, cancelling and deposits only through the same validated tools as WhatsApp; offer at most 3 slots per turn | Must |
| VOI-06 | Confirm the paying phone number aloud before an STK Push; confirm the booking only from the M-Pesa callback or status query (PAY-08) | Must |
| VOI-07 | After a booking or payment, send a WhatsApp confirmation (utility template) to the caller's number | Should |
| VOI-08 | Transfer to a staff extension on request ("person", "mtu"), on sensitive or emergency intent, or on low confidence; outside handover hours, take a message | Must |
| VOI-09 | Store each call as a conversation with channel voice: start, end, duration, outcome, actions, transfer target and a summary. No recordings or full transcripts unless the tenant enables them and the privacy review allows it | Must |
| VOI-10 | Time from end of caller speech to start of reply under 1.5 seconds at the 95th percentile | Must |
| VOI-11 | Booking task success rate of 90% or more in the spike test set | Must |
| VOI-12 | The caller can interrupt the assistant, and it stops speaking | Should |
| VOI-13 | Meter voice minutes per tenant in usage_daily and price voice separately from conversations | Must |
| VOI-14 | Summaries never include health details beyond "asked about symptoms; handed to staff" (BK-14, NFR-11) | Must |

## Data model additions (Milestone 9)

| Table (owner) | Key fields |
| --- | --- |
| voice_lines (voice) | tenant_id, did (E.164), sip_trunk_ref, status, created_at |
| calls (voice) | tenant_id, conversation_id, provider_call_id, from_number, did, started_at, answered_at, ended_at, duration_sec, outcome (answered, transferred, message_taken, abandoned, failed), transfer_target, summary |
| usage_daily (reports) | add voice_minutes, voice_cost_usd |

`conversations.channel` already exists (Milestone 2).

## Open questions

1. Asterisk or FreeSWITCH, and which SIP trunk provider? Where will the PBX be hosted?
2. Voice pricing: a bundle of minutes per plan, or per-minute overage?
3. Legal review: call transcription notice wording, and whether any tenant may enable recordings.
4. Does the speech-to-speech provider support tool calls on SIP calls? (Its SIP guide does not say so
   explicitly; the spike must confirm it.)
