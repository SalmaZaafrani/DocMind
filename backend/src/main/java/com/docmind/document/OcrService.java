package com.docmind.document;

import java.awt.image.BufferedImage;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.Tesseract;

/**
 * Reconnaissance de texte (Tesseract) pour les images et les PDF scannés.
 * Si Tesseract n'est pas installé sur la machine, l'OCR est simplement désactivé.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final String datapath;
    private final String language;
    private final boolean available;

    public OcrService(@Value("${docmind.ocr.datapath}") String datapath,
                      @Value("${docmind.ocr.language}") String language) {
        this.datapath = datapath;
        this.language = language;
        this.available = new File(datapath).isDirectory();
        if (!available) {
            log.warn("OCR désactivé : dossier de langues Tesseract introuvable ({})", datapath);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** Texte reconnu, ou chaîne vide si l'OCR est indisponible ou échoue. */
    public String read(BufferedImage image) {
        if (!available || image == null) {
            return "";
        }
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(datapath);
            tesseract.setLanguage(language);
            String text = tesseract.doOCR(image);
            return text == null ? "" : text.trim();
        } catch (Throwable e) {
            log.warn("OCR impossible : {}", e.getMessage());
            return "";
        }
    }
}
