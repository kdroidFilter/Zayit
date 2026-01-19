import { useRef, useEffect } from 'react';

interface Particle {
  x: number;
  y: number;
  baseX: number;
  baseY: number;
  size: number;
  color: 'gold' | 'silver';
  phase: number;
  floatSpeed: number;
  twinkleSpeed: number;
  xAmplitude: number;
  yAmplitude: number;
}

export function CrystalParticles({ count = 50 }: { count?: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const particlesRef = useRef<Particle[]>([]);
  const animationRef = useRef<number>(0);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d', { alpha: true });
    if (!ctx) return;

    // Set canvas size
    const updateSize = () => {
      const dpr = window.devicePixelRatio || 1;
      canvas.width = window.innerWidth * dpr;
      canvas.height = window.innerHeight * dpr;
      canvas.style.width = `${window.innerWidth}px`;
      canvas.style.height = `${window.innerHeight}px`;
      ctx.scale(dpr, dpr);
    };
    updateSize();

    // Initialize particles
    particlesRef.current = Array.from({ length: count }, () => ({
      x: 0,
      y: 0,
      baseX: Math.random() * window.innerWidth,
      baseY: Math.random() * window.innerHeight,
      size: Math.random() * 4 + 2,
      color: Math.random() > 0.5 ? 'gold' : 'silver',
      phase: Math.random() * Math.PI * 2,
      floatSpeed: 0.0003 + Math.random() * 0.0004,
      twinkleSpeed: 0.002 + Math.random() * 0.003,
      xAmplitude: 20 + Math.random() * 40,
      yAmplitude: 40 + Math.random() * 100,
    }));

    let lastTime = 0;

    const animate = (time: number) => {
      const deltaTime = time - lastTime;
      lastTime = time;

      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);

      particlesRef.current.forEach((p) => {
        // Update position with smooth floating motion
        p.phase += p.floatSpeed * deltaTime;
        p.x = p.baseX + Math.sin(p.phase) * p.xAmplitude;
        p.y = p.baseY + Math.cos(p.phase * 0.7) * p.yAmplitude;

        // Calculate twinkle opacity
        const twinkle = Math.sin(time * p.twinkleSpeed + p.phase) * 0.5 + 0.5;
        const opacity = 0.2 + twinkle * 0.8;

        // Color definitions
        const isGold = p.color === 'gold';
        const coreR = isGold ? 255 : 255;
        const coreG = isGold ? 235 : 255;
        const coreB = isGold ? 180 : 255;
        const glowR = isGold ? 230 : 200;
        const glowG = isGold ? 210 : 200;
        const glowB = isGold ? 140 : 230;

        // Draw outer glow
        const glowSize = p.size * (4 + twinkle * 4);
        const gradient = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, glowSize);
        gradient.addColorStop(0, `rgba(${coreR}, ${coreG}, ${coreB}, ${opacity * 0.9})`);
        gradient.addColorStop(0.3, `rgba(${glowR}, ${glowG}, ${glowB}, ${opacity * 0.5})`);
        gradient.addColorStop(0.6, `rgba(${glowR}, ${glowG}, ${glowB}, ${opacity * 0.2})`);
        gradient.addColorStop(1, `rgba(${glowR}, ${glowG}, ${glowB}, 0)`);

        ctx.beginPath();
        ctx.arc(p.x, p.y, glowSize, 0, Math.PI * 2);
        ctx.fillStyle = gradient;
        ctx.fill();

        // Draw bright core
        const coreSize = p.size * (0.5 + twinkle * 0.5);
        ctx.beginPath();
        ctx.arc(p.x, p.y, coreSize, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(${coreR}, ${coreG}, ${coreB}, ${opacity})`;
        ctx.fill();
      });

      animationRef.current = requestAnimationFrame(animate);
    };

    animationRef.current = requestAnimationFrame(animate);

    const handleResize = () => {
      updateSize();
      // Update particle base positions proportionally
      particlesRef.current.forEach((p) => {
        p.baseX = (p.baseX / window.innerWidth) * window.innerWidth;
        p.baseY = (p.baseY / window.innerHeight) * window.innerHeight;
      });
    };

    window.addEventListener('resize', handleResize);

    return () => {
      cancelAnimationFrame(animationRef.current);
      window.removeEventListener('resize', handleResize);
    };
  }, [count]);

  return (
    <canvas
      ref={canvasRef}
      className="fixed inset-0 pointer-events-none z-0"
      style={{ opacity: 0.9 }}
    />
  );
}
