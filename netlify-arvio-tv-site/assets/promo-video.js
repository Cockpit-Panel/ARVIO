(() => {
  const video = document.getElementById('promo-video');
  if (!video) return;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const connection = navigator.connection;
  let motionPaused = document.body.classList.contains('paused');
  let visible = false;
  let userPaused = false;
  let internalPauses = 0;
  let playPending = false;

  // Defer the MP4 request until the tour enters the viewport. Autoplay is
  // controlled here so reduced motion and Save-Data are honored before loading.
  video.autoplay = false;
  video.muted = true;

  function pause() {
    if (!video.paused) {
      internalPauses++;
      video.pause();
    }
  }

  async function update() {
    if (!visible || document.hidden) {
      pause();
      return;
    }
    if (!video.getAttribute('src')) video.src = video.dataset.src;
    if (userPaused || motionPaused || reducedMotion.matches || connection?.saveData || playPending || !video.paused) return;
    playPending = true;
    try {
      await video.play();
    } catch {
      // Browser autoplay restrictions leave native Play available.
    } finally {
      playPending = false;
      if (!visible || document.hidden || userPaused || motionPaused || reducedMotion.matches || connection?.saveData) pause();
    }
  }

  video.addEventListener('pause', () => {
    if (internalPauses > 0) internalPauses--;
    else userPaused = true;
  });
  video.addEventListener('play', () => {
    if (!playPending) userPaused = false;
  });
  document.addEventListener('visibilitychange', update);
  document.addEventListener('arvio:motionchange', event => {
    motionPaused = event.detail.paused;
    if (motionPaused) pause();
    else update();
  });
  function preferenceChanged() {
    if (reducedMotion.matches || connection?.saveData) pause();
    else update();
  }
  reducedMotion.addEventListener('change', preferenceChanged);
  connection?.addEventListener('change', preferenceChanged);

  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver(entries => {
      visible = entries.some(entry => entry.isIntersecting && entry.intersectionRatio >= 0.25);
      update();
    }, {threshold: [0, 0.25]});
    observer.observe(video);
  } else {
    video.src = video.dataset.src;
  }
})();
