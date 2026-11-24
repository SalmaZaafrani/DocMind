package com.docmind.agent;

import java.util.Map;

/** Une action de l'agent : outil appelé, paramètres, résultat résumé et durée. */
public record Step(String tool, String label, Map<String, Object> input, String output, long durationMs) { }
