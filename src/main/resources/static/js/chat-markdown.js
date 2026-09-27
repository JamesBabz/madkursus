/* Model output is untrusted. Only sanitized formatting enters the shared chat DOM. */
function renderChatMarkdown(element, text) {
  const html = marked.parse(text, { async: false, breaks: true });
  const fragment = DOMPurify.sanitize(html, {
    RETURN_DOM_FRAGMENT: true,
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'ul', 'ol', 'li', 'code', 'pre',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'blockquote', 'hr'],
    ALLOWED_ATTR: ['start'],
    ALLOW_DATA_ATTR: false,
    ALLOW_ARIA_ATTR: false
  });
  element.replaceChildren(fragment);
}
