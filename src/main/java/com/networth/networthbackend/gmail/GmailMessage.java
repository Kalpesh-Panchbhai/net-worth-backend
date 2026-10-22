package com.networth.networthbackend.gmail;

import java.time.Instant;
import java.util.List;

/**
 * One Gmail message as the sync sees it. Header-only fetches leave {@code body} null; the body is
 * downloaded only after a {@link BankEmailRule} has matched the headers.
 *
 * @param authResults the raw {@code Authentication-Results} header, used for the DMARC check
 * @param body        the plain-text body (HTML parts are reduced to text), or null if not fetched
 * @param pdfs        raw bytes of each PDF attachment, or empty if not fetched
 */
public record GmailMessage(String id, String from, String subject, String authResults, Instant receivedAt, String body,
                           List<byte[]> pdfs) {

    /** The bare address from a {@code From} header: {@code "HDFC Bank <alerts@hdfcbank.net>"} -> {@code alerts@hdfcbank.net}. */
    public String fromAddress() {
        if (from == null) return null;
        int lt = from.lastIndexOf('<');
        int gt = from.lastIndexOf('>');
        String addr = lt >= 0 && gt > lt ? from.substring(lt + 1, gt) : from;
        return addr.trim().toLowerCase();
    }

    /** True when Gmail recorded a DMARC pass, i.e. the sender's domain really sent it. */
    public boolean dmarcPassed() {
        return authResults != null && authResults.toLowerCase().contains("dmarc=pass");
    }

    public GmailMessage withBody(String newBody) {
        return new GmailMessage(id, from, subject, authResults, receivedAt, newBody, pdfs);
    }

    public GmailMessage withPdfs(List<byte[]> newPdfs) {
        return new GmailMessage(id, from, subject, authResults, receivedAt, body, newPdfs);
    }
}
