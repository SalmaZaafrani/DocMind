package com.docmind.agent;

/** Passage cité dans une réponse, numéroté [n]. */
public record Source(int n, Long documentId, String documentName, int page, String excerpt) { }
