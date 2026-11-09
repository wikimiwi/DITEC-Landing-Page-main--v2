/* Carrega a fonte Poppins sem bloquear a renderização da página (a CSP do site
   não permite o truque onload="" inline). Enquanto a fonte chega, o texto aparece
   com a fonte do sistema (font-display: swap). */
(function () {
  var l = document.createElement('link');
  l.rel = 'stylesheet';
  l.href = 'https://fonts.googleapis.com/css2?family=Poppins:wght@300;400;500;600;700;800&display=swap';
  document.head.appendChild(l);
})();
