package ng.proptech.web;

import ng.proptech.service.UssdSessionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public webhook matching Africa's Talking's USSD callback contract (SecurityConfig permits "/api/ussd/**"
 * with NO bearer token, since the telecom gateway cannot present one). In production this path should
 * additionally be restricted to Africa's Talking's published IP ranges at the reverse proxy / WAF.
 *
 * The gateway POSTs application/x-www-form-urlencoded fields (sessionId, serviceCode, phoneNumber, text)
 * and expects a text/plain body starting with "CON " (keep session open) or "END " (terminate).
 */
@RestController
@RequestMapping("/api/ussd")
public class UssdController {

    private final UssdSessionService ussdSessionService;

    public UssdController(UssdSessionService ussdSessionService) {
        this.ussdSessionService = ussdSessionService;
    }

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String callback(@RequestParam String sessionId,
                            @RequestParam(required = false) String serviceCode,
                            @RequestParam String phoneNumber,
                            @RequestParam(required = false, defaultValue = "") String text) {
        return ussdSessionService.handle(sessionId, phoneNumber, text);
    }
}
