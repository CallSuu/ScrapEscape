const materials = ['metal','gear','wire','circuit','plastic','rubber','battery','filter','alloy','signal'];
export function itemIconPath(id) {
  const name = typeof id === 'number' ? materials[id-1] : ['cleaner','medical','key'].includes(id) ? id : null;
  return name ? '/assets/items/' + name + '.svg?v=20261109' : null;
}
export function itemIcon(id) {
  const path = itemIconPath(id);
  if (!path) return document.createTextNode('');
  const image = document.createElement('img'); image.src=path; image.alt=''; image.className='se-item-icon'; image.width=40; image.height=40;
  return image;
}
