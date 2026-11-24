package com.docmind.document;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import com.docmind.common.ApiException;

/** Extrait le texte d'un fichier, page par page (PDF) ou par sections (autres formats). */
@Component
public class TextExtractor {

    public record Extraction(List<String> pages, boolean ocrUsed) { }

    private static final int SECTION_SIZE = 3000;
    private static final int MAX_OCR_PAGES = 30;

    private final OcrService ocr;

    public TextExtractor(OcrService ocr) {
        this.ocr = ocr;
    }

    public static boolean isSupported(String filename) {
        String ext = extension(filename);
        return List.of("pdf", "docx", "txt", "md", "csv", "png", "jpg", "jpeg").contains(ext);
    }

    public Extraction extract(String filename, byte[] data) {
        String ext = extension(filename);
        try {
            return switch (ext) {
                case "pdf" -> pdf(data);
                case "docx" -> new Extraction(sections(docx(data)), false);
                case "txt", "md", "csv" -> new Extraction(sections(new String(data, StandardCharsets.UTF_8)), false);
                case "png", "jpg", "jpeg" -> image(data);
                default -> throw ApiException.badRequest("Format non pris en charge : " + ext);
            };
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Lecture du fichier impossible : " + e.getMessage());
        }
    }

    private Extraction pdf(byte[] data) throws Exception {
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = null;
            List<String> pages = new ArrayList<>();
            boolean ocrUsed = false;
            int total = doc.getNumberOfPages();
            for (int i = 1; i <= total; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = clean(stripper.getText(doc));
                // Page sans texte : probablement scannée, on tente l'OCR
                if (text.length() < 20 && ocr.isAvailable() && i <= MAX_OCR_PAGES) {
                    if (renderer == null) {
                        renderer = new PDFRenderer(doc);
                    }
                    BufferedImage image = renderer.renderImageWithDPI(i - 1, 200, ImageType.GRAY);
                    String recognized = clean(ocr.read(image));
                    if (!recognized.isEmpty()) {
                        text = recognized;
                        ocrUsed = true;
                    }
                }
                pages.add(text);
            }
            return new Extraction(pages, ocrUsed);
        }
    }

    private String docx(byte[] data) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private Extraction image(byte[] data) throws Exception {
        if (!ocr.isAvailable()) {
            throw ApiException.badRequest("L'OCR n'est pas disponible sur ce serveur : impossible de lire une image");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw ApiException.badRequest("Image illisible");
        }
        return new Extraction(List.of(clean(ocr.read(image))), true);
    }

    /** Découpe un long texte en sections d'environ 3 000 caractères, sur des fins de paragraphe. */
    static List<String> sections(String raw) {
        String text = clean(raw);
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String para : text.split("\n\\s*\n")) {
            if (current.length() > 0 && current.length() + para.length() > SECTION_SIZE) {
                out.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(para).append("\n\n");
        }
        if (current.toString().trim().length() > 0) {
            out.add(current.toString().trim());
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", "")
                .replace(' ', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    static String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
