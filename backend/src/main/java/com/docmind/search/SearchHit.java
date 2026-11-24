package com.docmind.search;

/** Passage trouvé par la recherche, avec son score de pertinence. */
public record SearchHit(Long chunkId, Long documentId, String documentName, int page, String text, double score) { }
