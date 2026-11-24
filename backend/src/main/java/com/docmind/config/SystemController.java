package com.docmind.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docmind.document.OcrService;
import com.docmind.llm.GroqClient;

@RestController
public class SystemController {

    public record Status(String status, boolean llm, String model, boolean ocr) { }

    private final GroqClient llm;
    private final OcrService ocr;

    public SystemController(GroqClient llm, OcrService ocr) {
        this.llm = llm;
        this.ocr = ocr;
    }

    @GetMapping("/api/system/status")
    public Status status() {
        return new Status("ok", llm.isEnabled(), llm.isEnabled() ? llm.model() : null, ocr.isAvailable());
    }
}
