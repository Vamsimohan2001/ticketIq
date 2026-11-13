package com.ticketiq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticketiq.core.redaction.PiiRedactor;
import org.junit.jupiter.api.Test;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    void replacesEmailAddresses() {
        PiiRedactor.Result r = redactor.redact("Write to asha.rao+work@example.co.in or bob@corp.com please");
        assertEquals("Write to [EMAIL] or [EMAIL] please", r.text());
        assertEquals(2, r.counts().get("EMAIL").intValue());
    }

    @Test
    void replacesIndianMobileNumbersInCommonFormats() {
        assertEquals("Call [PHONE] now", redactor.redactText("Call +91 98765 43210 now"));
        assertEquals("Call [PHONE] now", redactor.redactText("Call 9876543210 now"));
        assertEquals("Call [PHONE] now", redactor.redactText("Call 98765-43210 now"));
        assertEquals("Call [PHONE] now", redactor.redactText("Call 91 9876543210 now"));
    }

    @Test
    void replacesInternationalPhoneNumbers() {
        assertEquals("Reach me on [PHONE]", redactor.redactText("Reach me on +44 20 7946 0958"));
    }

    @Test
    void replacesPanNumbers() {
        assertEquals("My PAN is [PAN].", redactor.redactText("My PAN is ABCDE1234F."));
    }

    @Test
    void replacesAadhaarLikeTwelveDigitNumbers() {
        assertEquals("ID [ID] attached", redactor.redactText("ID 2345 6789 0123 attached"));
        assertEquals("ID [ID] attached", redactor.redactText("ID 234567890123 attached"));
    }

    @Test
    void replacesValidCardNumbersButNotRandomLongNumbers() {
        // 4111 1111 1111 1111 is the well-known test Visa number and passes the Luhn check
        assertEquals("Card [CARD] was charged", redactor.redactText("Card 4111 1111 1111 1111 was charged"));
        assertEquals("Card [CARD] was charged", redactor.redactText("Card 4111-1111-1111-1111 was charged"));
        String invalid = "Reference 1234 5678 9012 3456 stays";
        assertFalse(redactor.redactText(invalid).contains("[CARD]"), "Luhn-invalid numbers are not cards");
    }

    @Test
    void leavesOrdinaryTextAndSmallNumbersAlone() {
        String text = "Report 42 failed at 10:30 with error 500 for 3 users";
        PiiRedactor.Result r = redactor.redact(text);
        assertEquals(text, r.text());
        assertEquals(0, r.total());
    }

    @Test
    void countsEveryKindInOneTicket() {
        PiiRedactor.Result r = redactor.redact(
                "I am ravi@x.com, phone 9876543210, PAN ABCDE1234F, card 4111 1111 1111 1111.");
        assertEquals(4, r.total());
        assertTrue(r.text().contains("[EMAIL]") && r.text().contains("[PHONE]")
                && r.text().contains("[PAN]") && r.text().contains("[CARD]"));
        assertFalse(r.text().contains("ravi@x.com"));
    }

    @Test
    void handlesNullAndEmptyInput() {
        assertEquals("", redactor.redactText(null));
        assertEquals("", redactor.redactText(""));
    }
}
