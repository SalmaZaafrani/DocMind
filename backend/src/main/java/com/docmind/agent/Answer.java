package com.docmind.agent;

import java.util.List;

/** Réponse produite par le mode RAG ou par l'agent. */
public record Answer(String content, List<Source> sources, List<Step> steps, String model) { }
