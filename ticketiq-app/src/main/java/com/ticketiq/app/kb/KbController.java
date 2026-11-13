package com.ticketiq.app.kb;

import com.ticketiq.app.kb.KbService.KbRequest;
import com.ticketiq.app.kb.KbService.KbResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kb")
public class KbController {

    private final KbService kb;

    public KbController(KbService kb) {
        this.kb = kb;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public List<KbResponse> list() {
        return kb.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public KbResponse create(@Valid @RequestBody KbRequest request) {
        return kb.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public KbResponse update(@PathVariable Long id, @Valid @RequestBody KbRequest request) {
        return kb.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        kb.delete(id);
    }
}
