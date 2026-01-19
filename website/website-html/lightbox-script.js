// Working lightbox functionality
function openLightbox(src) {
    document.getElementById('lightbox').style.display = 'flex';
    document.getElementById('lightboxImage').src = src;
}

function closeLightbox() {
    document.getElementById('lightbox').style.display = 'none';
}

// Add click handlers to screenshots
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.screenshot').forEach(function(img) {
        img.onclick = function() {
            openLightbox(this.src);
        };
    });
});

// Basic theme functionality
let isDark = localStorage.getItem('theme') !== 'light';

function toggleTheme() {
    isDark = !isDark;
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
    
    if (isDark) {
        document.documentElement.classList.add('dark');
    } else {
        document.documentElement.classList.remove('dark');
    }
}

// Basic language functionality  
let isHebrew = localStorage.getItem('language') === 'he';

function toggleLanguage() {
    isHebrew = !isHebrew;
    localStorage.setItem('language', isHebrew ? 'he' : 'en');
    
    document.documentElement.dir = isHebrew ? 'rtl' : 'ltr';
    
    const langToggle = document.getElementById('langToggle');
    if (langToggle) {
        langToggle.textContent = isHebrew ? 'en' : 'ע';
    }
    
    document.querySelectorAll('.en-text').forEach(el => {
        el.classList.toggle('hidden', isHebrew);
    });
    document.querySelectorAll('.he-text').forEach(el => {
        el.classList.toggle('hidden', !isHebrew);
    });
}

function scrollToSection(sectionId) {
    const section = document.getElementById(sectionId);
    if (section) {
        section.scrollIntoView({
            behavior: 'smooth',
            block: 'start'
        });
    }
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    if (isDark) {
        document.documentElement.classList.add('dark');
    }
    
    if (isHebrew) {
        document.documentElement.dir = 'rtl';
        const langToggle = document.getElementById('langToggle');
        if (langToggle) langToggle.textContent = 'en';
        document.querySelectorAll('.en-text').forEach(el => el.classList.add('hidden'));
        document.querySelectorAll('.he-text').forEach(el => el.classList.remove('hidden'));
    }
});