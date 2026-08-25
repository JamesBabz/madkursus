function keepFocusedDialogControlVisible(viewport) {
  window.requestAnimationFrame(() => {
    const activeControl = document.activeElement;
    const dialog = activeControl?.closest?.('dialog[open]');
    if (!dialog) return;

    const header = dialog.querySelector(':scope > .section-heading, :scope > form > .section-heading:first-child');
    const actions = dialog.querySelector(':scope > .dialog-actions:last-child, :scope > form > .dialog-actions:last-child');
    const controlBounds = activeControl.getBoundingClientRect();
    const visibleTop = Math.max(viewport.offsetTop, header?.getBoundingClientRect().bottom ?? viewport.offsetTop);
    const viewportBottom = viewport.offsetTop + viewport.height;
    const visibleBottom = Math.min(viewportBottom, actions?.getBoundingClientRect().top ?? viewportBottom);

    if (controlBounds.top < visibleTop || controlBounds.bottom > visibleBottom) {
      activeControl.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
  });
}

function updateDialogViewport() {
  if (!window.visualViewport) return;
  const viewport = window.visualViewport;
  const keyboardInset = Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop);
  document.documentElement.style.setProperty('--dialog-viewport-height', `${Math.round(viewport.height)}px`);
  document.documentElement.style.setProperty('--dialog-viewport-bottom', `${Math.round(keyboardInset)}px`);
  keepFocusedDialogControlVisible(viewport);
}

if (window.visualViewport) {
  updateDialogViewport();
  window.visualViewport.addEventListener('resize', updateDialogViewport);
  window.visualViewport.addEventListener('scroll', updateDialogViewport);
}
