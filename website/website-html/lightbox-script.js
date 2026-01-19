// Global state
let isDark = false;
let isHebrew = false;
let lightboxImages = [];
let currentImageIndex = 0;

// --- Initialization ---

function initialize() {
    console.log('DOM loaded, initializing...');
    
    // Set initial theme
    isDark = localStorage.getItem('theme') === 'light' ? false : 
             (localStorage.getItem('theme') === 'dark' || !localStorage.getItem('theme') || window.matchMedia('(prefers-color-scheme: dark)').matches);
    applyTheme();

    // Set initial language
    isHebrew = localStorage.getItem('language') === 'he' || 
               (localStorage.getItem('language') === 'en' ? false : 
                (navigator.language.startsWith('he') || navigator.languages.some(lang => lang.startsWith('he'))));
    applyLanguage();
    
    // Initialize components
    loadDownloadCount();
    initializePageIndicators();
    initializeLightbox();
    initializeScrollPanels();

    // Event listeners for theme/language are handled by onclick attributes in HTML
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
        if (!localStorage.getItem('theme')) {
            isDark = e.matches;
            applyTheme();
        }
    });

    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        const href = anchor.getAttribute('href');
        if (href && href !== '#') {
            anchor.addEventListener('click', function (e) {
                e.preventDefault();
                const targetId = href;
                const target = document.querySelector(targetId);
                if (target) {
                    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
            });
        }
    });

    console.log('Initialization complete.');
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialize);
} else {
    initialize();
}

function initializeScrollPanels() {
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) return;

    const frames = document.querySelectorAll('.preview-frame');
    if (frames.length === 0) return;

    frames.forEach(frame => frame.classList.add('scroll-reveal'));

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            entry.target.classList.toggle('in-view', entry.isIntersecting);
        });
    }, {
        threshold: 0.35,
        rootMargin: '0px 0px -10% 0px'
    });

    frames.forEach(frame => observer.observe(frame));
}

// --- Theme Management ---

function applyTheme() {
    const lightImages = document.querySelectorAll('.light-image');
    const darkImages = document.querySelectorAll('.dark-image');
    
    if (isDark) {
        document.documentElement.classList.add('dark');
        document.querySelector('.theme-icon-light')?.classList.add('hidden');
        document.querySelector('.theme-icon-dark')?.classList.remove('hidden');
        
        lightImages.forEach(img => img.classList.add('hidden'));
        darkImages.forEach(img => img.classList.remove('hidden'));
    } else {
        document.documentElement.classList.remove('dark');
        document.querySelector('.theme-icon-light')?.classList.remove('hidden');
        document.querySelector('.theme-icon-dark')?.classList.add('hidden');
        
        lightImages.forEach(img => img.classList.remove('hidden'));
        darkImages.forEach(img => img.classList.add('hidden'));
    }
    // After theme change, lightbox images need to be re-evaluated
    rebuildLightboxImageCache();
}

function toggleTheme() {
    isDark = !isDark;
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
    applyTheme();
}


// --- Language Management ---

function applyLanguage() {
    document.documentElement.dir = isHebrew ? 'rtl' : 'ltr';
    
    const langToggle = document.getElementById('langToggle');
    if (langToggle) {
        langToggle.textContent = isHebrew ? 'en' : 'ע';
    }
    
    document.querySelectorAll('.en-text').forEach(el => el.classList.toggle('hidden', isHebrew));
    document.querySelectorAll('.he-text').forEach(el => el.classList.toggle('hidden', !isHebrew));
    
    updatePageIndicatorLabels();
}

function toggleLanguage() {
    isHebrew = !isHebrew;
    localStorage.setItem('language', isHebrew ? 'he' : 'en');
    applyLanguage();
}

// --- Lightbox ---

function initializeLightbox() {
    rebuildLightboxImageCache();
    
    document.querySelectorAll('.screenshot').forEach((img) => {
        img.addEventListener('click', (e) => {
            // Find the index of the clicked image within the *currently visible* images
            const clickedSrc = e.target.src;
            const visibleIndex = lightboxImages.findIndex(src => src === clickedSrc);
            if (visibleIndex > -1) {
                openLightbox(visibleIndex);
            }
        });
    });
    
    // Keyboard navigation
    document.addEventListener('keydown', (e) => {
        if (document.getElementById('lightbox').style.display !== 'flex') return;

        if (e.key === 'ArrowRight') nextImage();
        else if (e.key === 'ArrowLeft') prevImage();
        else if (e.key === 'Escape') closeLightbox();
    });
}

function rebuildLightboxImageCache() {
    lightboxImages = [];
    document.querySelectorAll('.screenshot').forEach(img => {
        // An image is considered part of the gallery if it's currently visible (not display:none)
        if (img.offsetParent !== null) {
            lightboxImages.push(img.src);
        }
    });
    console.log(`Lightbox image cache rebuilt: ${lightboxImages.length} images found.`);
}

function openLightbox(index) {
    if (lightboxImages.length === 0) {
        rebuildLightboxImageCache(); // Try to rebuild if it's empty
    }
    if (lightboxImages.length === 0) {
        console.error("No images found for lightbox.");
        return;
    }
    
    document.getElementById('lightbox').style.display = 'flex';
    showImage(index);
}

function closeLightbox() {
    document.getElementById('lightbox').style.display = 'none';
}

function showImage(index) {
    if (index < 0 || index >= lightboxImages.length) {
        console.warn(`Image index ${index} is out of bounds.`);
        return;
    }
    currentImageIndex = index;
    document.getElementById('lightboxImage').src = lightboxImages[index];

    // Show/hide nav buttons
    document.querySelector('.lightbox-prev').style.display = (index > 0) ? 'block' : 'none';
    document.querySelector('.lightbox-next').style.display = (index < lightboxImages.length - 1) ? 'block' : 'none';
}

function nextImage() {
    showImage(currentImageIndex + 1);
}

function prevImage() {
    showImage(currentImageIndex - 1);
}


// --- Page Indicators ---

function initializePageIndicators() {
    const sections = document.querySelectorAll('.page-section');
    if (sections.length === 0) return;
    
    const observer = new IntersectionObserver((entries) => {
        // Find the most visible section
        let mostVisibleSection = null;
        let maxVisibility = 0;
        
        entries.forEach(entry => {
            if (entry.isIntersecting && entry.intersectionRatio > maxVisibility) {
                maxVisibility = entry.intersectionRatio;
                mostVisibleSection = entry.target;
            }
        });
        
        // Update the active indicator if we found a visible section
        if (mostVisibleSection) {
            updateActiveIndicator(mostVisibleSection.id);
        }
    }, { 
        threshold: [0.1, 0.3, 0.5, 0.7, 0.9], // Multiple thresholds for better detection
        rootMargin: '-10% 0px -10% 0px' // Reduce top/bottom margin to focus on center
    });

    sections.forEach(section => observer.observe(section));
    updatePageIndicatorLabels();
}

function updateActiveIndicator(sectionId) {
    document.querySelectorAll('.page-indicator').forEach(indicator => {
        indicator.classList.toggle('active', indicator.dataset.section === sectionId);
    });
    document.body.setAttribute('data-section', sectionId);
}

function updatePageIndicatorLabels() {
    document.querySelectorAll('.page-indicator').forEach(indicator => {
        const label = indicator.getAttribute(isHebrew ? 'data-label-he' : 'data-label-en');
        indicator.setAttribute('data-current-label', label);
    });
}

function scrollToSection(sectionId) {
    const section = document.getElementById(sectionId);
    if (section) {
        section.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

// --- Other ---

async function loadDownloadCount() {
    try {
        const response = await fetch('download-count.json');
        const data = await response.json();
        const downloadStats = document.getElementById('downloadStats');
        if (downloadStats && data.count) {
            downloadStats.innerHTML = `
                <div class="stat-number">+${data.count.toLocaleString()}</div>
                <div class="stat-label">
                    <span class="en-text">Downloads</span>
                    <span class="he-text ${isHebrew ? '' : 'hidden'}">הורדות</span>
                </div>
            `;
            // Ensure correct language is shown on load
             if (isHebrew) {
                downloadStats.querySelector('.en-text').classList.add('hidden');
                downloadStats.querySelector('.he-text').classList.remove('hidden');
            }
        }
    } catch (error) {
        console.log('Could not load download count');
    }
}
