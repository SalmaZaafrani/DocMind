/** Rendu markdown minimal et sûr (texte échappé) : titres, gras, italique, code, listes, citations [n]. */
export function renderMarkdown(text: string, citations: Set<number> = new Set()): string {
  const escape = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const inline = (s: string) =>
    escape(s)
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
      .replace(/(^|\s)_([^_\n]+)_(?=\s|$|[.,;:])/g, '$1<em>$2</em>')
      .replace(/\[(\d{1,2})\]/g, (m, n) =>
        citations.has(+n) ? `<span class="cite cite-${n}" title="Voir la source ${n}">${n}</span>` : m,
      );

  // Certains modèles citent au format 【1†L3-L4】 : on le ramène à [1]
  const lines = text.replace(/\r/g, '').replace(/【(\d+)[^】]*】/g, '[$1]').split('\n');
  const out: string[] = [];
  let list: 'ul' | 'ol' | null = null;
  let tableRows = 0;
  const closeList = () => {
    if (list) out.push(`</${list}>`);
    list = null;
    if (tableRows) out.push('</tbody></table>');
    tableRows = 0;
  };
  for (const raw of lines) {
    const line = raw.trimEnd();
    const bullet = line.match(/^\s*[-*•]\s+(.*)$/);
    const numbered = line.match(/^\s*\d+[.)]\s+(.*)$/);
    const heading = line.match(/^(#{1,4})\s+(.*)$/);
    if (bullet || numbered) {
      const type = bullet ? 'ul' : 'ol';
      if (list !== type) {
        closeList();
        out.push(`<${type}>`);
        list = type;
      }
      out.push(`<li>${inline((bullet ?? numbered)![1])}</li>`);
    } else if (heading) {
      closeList();
      out.push(`<h4>${inline(heading[2])}</h4>`);
    } else if (line.trim() === '') {
      closeList();
    } else if (/^\|.*\|$/.test(line.trim())) {
      if (list) {
        out.push(`</${list}>`);
        list = null;
      }
      if (/^\|[\s:|-]+\|$/.test(line.trim())) continue;
      const tag = tableRows === 0 ? 'th' : 'td';
      const cells = line.trim().slice(1, -1).split('|').map((c) => `<${tag}>${inline(c.trim())}</${tag}>`);
      if (tableRows === 0) out.push('<table><tbody>');
      out.push(`<tr>${cells.join('')}</tr>`);
      tableRows++;
    } else {
      closeList();
      out.push(`<p>${inline(line)}</p>`);
    }
  }
  closeList();
  return out.join('');
}
