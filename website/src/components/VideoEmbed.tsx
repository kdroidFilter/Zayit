import { useState } from 'react';
import { Play } from 'lucide-react';

interface VideoEmbedProps {
  videoId: string;
  title: string;
}

/**
 * YouTube video that shows its thumbnail first and loads the player only on click:
 * no third-party script or cookie until the reader asks for the video.
 */
export function VideoEmbed({ videoId, title }: VideoEmbedProps) {
  const [playing, setPlaying] = useState(false);

  return (
    <div
      className="relative w-full aspect-video rounded-xl md:rounded-2xl overflow-hidden"
      style={{
        boxShadow: '0 25px 80px rgba(0, 0, 0, 0.35), 0 0 0 1px rgba(230, 210, 140, 0.15)',
        background: '#000',
      }}
    >
      {playing ? (
        <iframe
          className="absolute inset-0 w-full h-full"
          src={`https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&rel=0`}
          title={title}
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
          referrerPolicy="strict-origin-when-cross-origin"
          allowFullScreen
        />
      ) : (
        <button
          type="button"
          onClick={() => setPlaying(true)}
          className="group absolute inset-0 w-full h-full cursor-pointer"
          aria-label={title}
        >
          <img
            src={`https://i.ytimg.com/vi/${videoId}/maxresdefault.jpg`}
            alt=""
            loading="lazy"
            decoding="async"
            className="absolute inset-0 w-full h-full object-cover transition-transform duration-500 group-hover:scale-[1.02]"
          />
          <span className="absolute inset-0 bg-black/25 transition-colors group-hover:bg-black/10" />
          <span
            className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 flex items-center justify-center w-16 h-16 md:w-20 md:h-20 rounded-full transition-transform group-hover:scale-110"
            style={{
              background: 'var(--gold)',
              boxShadow: '0 0 40px rgba(230, 210, 140, 0.5)',
            }}
          >
            <Play size={32} fill="#1a1a1a" color="#1a1a1a" className="ms-1" />
          </span>
        </button>
      )}
    </div>
  );
}
